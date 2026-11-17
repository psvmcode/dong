package com.dong.ad.handler;

import com.dong.ad.dto.AdChargeCommand;
import com.dong.ad.service.AdSettlementService;
import com.dong.ad.support.AdTopics;
import com.dong.common.util.JsonUtils;
import com.dong.framework.mq.MessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 计费消息处理器。本地总线与 RocketMQ 共用同一套处理逻辑，
 * 因此切换消息中间件不需要改计费代码。
 *
 * <p>畸形消息记日志后直接丢弃并返回 true：让它无限重投只会堵死队列，
 * 救不回一条本来就缺字段的消息。重复消息同样返回 true，
 * 因为幂等拦截是正常结局而不是失败，返回 false 会让容器反复重投。
 *
 * <p>只有数据库等瞬时故障才让异常抛出去：RocketMQ 会重投，
 * 本地总线吞掉后由对账任务扫 PENDING 事件补投，两条路径最终都收敛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdChargeEventHandler implements MessageHandler {

    /**
     * adSettlementService，业务服务层。
     */
    private final AdSettlementService adSettlementService;

    /**
     * 收到消息数。
     */
    private final LongAdder received = new LongAdder();

    /**
     * 被幂等拦截的重复消息数。
     */
    private final LongAdder duplicated = new LongAdder();

    /**
     * 丢弃的畸形消息数。
     */
    private final LongAdder malformed = new LongAdder();

    /**
     * 返回监听的主题。
     */
    @Override
    public String topic() {
        return AdTopics.CHARGE;
    }

    /**
     * 处理计费消息。
     */
    @Override
    public boolean handle(String key, String payload) {
        AdChargeCommand command;
        try {
            command = JsonUtils.fromJson(payload, AdChargeCommand.class);
        } catch (Exception ex) {
            malformed.increment();
            log.error("malformed ad charge message dropped key={} payload={}", key, payload, ex);
            return true;
        }
        if (command == null || command.eventId() == null || command.traceId() == null || command.statDate() == null
                || command.campaignId() == null || command.advertiserId() == null || command.amountCents() == null
                || command.eventType() == null || command.chargeMode() == null) {
            malformed.increment();
            log.error("ad charge message missing required fields dropped key={} payload={}", key, payload);
            return true;
        }
        received.increment();
        boolean handled = adSettlementService.charge(command);
        if (!handled) {
            duplicated.increment();
            log.info("duplicate ad charge message ignored eventId={}", command.eventId());
        }
        return true;
    }

    /**
     * 生成消费侧计数快照。
     *
     * @return 计数快照
     */
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("received", received.sum());
        stats.put("duplicated", duplicated.sum());
        stats.put("malformed", malformed.sum());
        return stats;
    }

}
