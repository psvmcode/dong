package com.dong.crossborder.dto;

import com.dong.crossborder.entity.RemittanceEvent;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * 汇款单流转日志响应。
 *
 * <p>状态名与编码一起返回：编码用于程序判断，状态名用于人看。
 * 排查问题时最需要的是「什么时候、谁、因为什么、从什么状态到什么状态」，
 * 这几项一个都不能少。
 */
@Data

public class RemittanceEventResponse {

    /**
     * 所属汇款单号
     */
    private String remittanceNo;

    /**
     * 变更前状态编码
     */
    private Integer fromStatus;

    /**
     * 变更前状态名
     */
    private String fromStatusName;

    /**
     * 变更后状态编码
     */
    private Integer toStatus;

    /**
     * 变更后状态名
     */
    private String toStatusName;

    /**
     * 触发动作名
     */
    private String event;

    /**
     * 结果：1 成功 0 被拒绝
     */
    private Integer result;

    /**
     * 说明，被拒绝时是原因
     */
    private String reason;

    /**
     * 操作人或来源标识
     */
    private String operator;

    /**
     * 发生时间
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换。
     *
     * @param event 流转日志实体
     * @return 响应对象
     */
    public static RemittanceEventResponse from(RemittanceEvent event) {
        RemittanceEventResponse response = new RemittanceEventResponse();
        response.setRemittanceNo(event.getRemittanceNo());
        response.setFromStatus(event.getFromStatus());
        response.setToStatus(event.getToStatus());
        response.setEvent(event.getEvent());
        response.setResult(event.getResult());
        response.setReason(event.getReason());
        response.setOperator(event.getOperator());
        response.setCreateTime(event.getCreateTime());
        if (event.getFromStatus() != null) {
            response.setFromStatusName(com.dong.crossborder.enums.RemittanceStatus.of(event.getFromStatus()).name());
        }
        if (event.getToStatus() != null) {
            response.setToStatusName(com.dong.crossborder.enums.RemittanceStatus.of(event.getToStatus()).name());
        }
        return response;
    }

}
