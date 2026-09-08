package com.dong.crossborder.service.impl;

import com.dong.crossborder.dto.RemittanceEventResponse;
import com.dong.crossborder.entity.RemittanceEvent;
import com.dong.crossborder.enums.RemittanceStatus;
import com.dong.crossborder.mapper.RemittanceEventMapper;
import com.dong.crossborder.service.RemittanceEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * 流转日志实现。
 *
 * <p>写日志失败不能影响主流程：日志是排查手段，不是业务本身。
 * 因为记不下日志而让一笔已经扣款的汇款失败，是把手段看得比目的还重。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class RemittanceEventServiceImpl implements RemittanceEventService {

    /**
     * eventMapper。
     */
    private final RemittanceEventMapper eventMapper;

    /**
     * 记录一次成功的流转。
     */
    @Override
    public void record(String remittanceNo, RemittanceStatus from, RemittanceStatus to,
                       String event, String operator) {
        write(remittanceNo, from, to, event, 1, "", operator);
    }

    /**
     * 记录一次被拒绝的流转，状态保持不变。
     */
    @Override
    public void recordRejected(String remittanceNo, RemittanceStatus current, String event,
                               String reason, String operator) {
        write(remittanceNo, current, current, event, 0, reason, operator);
    }

    /**
     * 查询完整流转历史。
     */
    @Override
    public List<RemittanceEventResponse> history(String remittanceNo) {
        return eventMapper.selectByRemittanceNo(remittanceNo).stream()
                .map(RemittanceEventResponse::from)
                .toList();
    }

    /**
     * 落库，异常只记录不抛出。
     */
    private void write(String remittanceNo, RemittanceStatus from, RemittanceStatus to,
                       String event, int result, String reason, String operator) {
        RemittanceEvent record = new RemittanceEvent();
        record.setRemittanceNo(remittanceNo);
        record.setFromStatus(from == null ? 0 : from.getCode());
        record.setToStatus(to == null ? 0 : to.getCode());
        record.setEvent(event == null ? "" : trim(event, 32));
        record.setResult(result);
        record.setReason(reason == null ? "" : trim(reason, 255));
        record.setOperator(operator == null ? "" : trim(operator, 64));
        try {
            eventMapper.insert(record);
        } catch (Exception ex) {
            log.error("write remittance event failed remittanceNo={} event={}", remittanceNo, event, ex);
        }
    }

    /**
     * 按字段长度截断，避免超长文本导致插入失败。
     */
    private String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

}
