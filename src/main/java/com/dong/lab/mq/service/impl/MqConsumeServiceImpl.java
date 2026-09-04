package com.dong.lab.mq.service.impl;

import com.dong.lab.mq.entity.MqMessageLog;
import com.dong.lab.mq.enums.MqMessageStatus;
import com.dong.lab.mq.mapper.MqMessageLogMapper;
import com.dong.lab.mq.service.MqConsumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
/**
 * 消息消费实现。所有传输共用同一套处理逻辑，
 * 幂等靠消息日志的唯一索引保证。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class MqConsumeServiceImpl implements MqConsumeService {

    private static final int MAX_RETRY = 3;

    /**
     * mqMessageLogMapper，MyBatis Mapper 数据访问层。
     */
    private final MqMessageLogMapper mqMessageLogMapper;

    private final LongAdder consumed = new LongAdder();

    private final LongAdder duplicated = new LongAdder();

    private final LongAdder deadLettered = new LongAdder();

    /**
     * consume。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean consume(String topic, String msgId, String payload) {
        if (mqMessageLogMapper.countByMsgId(msgId) > 0) {
            duplicated.increment();
            log.warn("duplicate message skipped topic={} msgId={}", topic, msgId);
            return true;
        }

        int retries = 0;
        while (retries < MAX_RETRY) {
            try {
                MqMessageLog logRecord = new MqMessageLog();
                logRecord.setMsgId(msgId);
                logRecord.setTopic(topic);
                logRecord.setPayload(payload);
                logRecord.setStatus(MqMessageStatus.CONSUMED);
                logRecord.setRetryCount(retries);
                mqMessageLogMapper.insert(logRecord);
                consumed.increment();
                log.info("message consumed topic={} msgId={}", topic, msgId);
                return true;
            } catch (DuplicateKeyException ex) {
                duplicated.increment();
                log.warn("unique index rejected a duplicate message msgId={}", msgId);
                return true;
            } catch (Exception ex) {
                retries++;
                log.warn("consume failed topic={} msgId={} attempt={} reason={}", topic, msgId, retries, ex.getMessage());
            }
        }

        MqMessageLog deadLetter = new MqMessageLog();
        deadLetter.setMsgId(msgId);
        deadLetter.setTopic(topic);
        deadLetter.setPayload(payload);
        deadLetter.setStatus(MqMessageStatus.DEAD_LETTERED);
        deadLetter.setRetryCount(retries);
        try {
            mqMessageLogMapper.insert(deadLetter);
        } catch (DuplicateKeyException ex) {
            mqMessageLogMapper.updateStatus(msgId, MqMessageStatus.DEAD_LETTERED.getCode());
        }
        deadLettered.increment();
        log.error("message moved to dead letter topic={} msgId={}", topic, msgId);
        return false;
    }

    /**
     * recent。
     */
    @Override
    public List<MqMessageLog> recent(int limit) {
        return mqMessageLogMapper.selectRecent(limit);
    }

    /**
     * stats。
     */
    @Override
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("consumed", consumed.sum());
        stats.put("duplicated", duplicated.sum());
        stats.put("deadLettered", deadLettered.sum());
        stats.put("totalInDb", mqMessageLogMapper.countAll());
        return stats;
    }

}
