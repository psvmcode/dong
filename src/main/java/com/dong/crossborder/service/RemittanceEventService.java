package com.dong.crossborder.service;

import com.dong.crossborder.dto.RemittanceEventResponse;
import com.dong.crossborder.enums.RemittanceStatus;

import java.util.List;
/**
 * 汇款单流转日志。
 *
 * <p>记录每一次状态推进的尝试，成功与被拒绝都记。
 * 只记成功的话，遇到「这笔钱为什么卡了两天」这类问题时就无从下手。
 */
public interface RemittanceEventService {

    /**
     * 记录一次成功的流转。
     *
     * @param remittanceNo 汇款单号
     * @param from         变更前状态
     * @param to           变更后状态
     * @param event        触发动作名
     * @param operator     操作人或来源标识
     */
    void record(String remittanceNo, RemittanceStatus from, RemittanceStatus to, String event, String operator);

    /**
     * 记录一次被拒绝的流转。状态没变，但要留下原因。
     *
     * @param remittanceNo 汇款单号
     * @param current      当前状态，未发生变化
     * @param event        触发动作名
     * @param reason       拒绝原因
     * @param operator     操作人或来源标识
     */
    void recordRejected(String remittanceNo, RemittanceStatus current, String event, String reason, String operator);

    /**
     * 查询某笔汇款的完整流转历史。
     *
     * @param remittanceNo 汇款单号
     * @return 按发生顺序排列的流转记录
     */
    List<RemittanceEventResponse> history(String remittanceNo);

}
