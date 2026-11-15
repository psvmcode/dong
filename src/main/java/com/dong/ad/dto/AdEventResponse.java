package com.dong.ad.dto;

import com.dong.ad.enums.AdChargeStatus;
import com.dong.ad.enums.AdEventType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 事件上报响应。
 *
 * <p>重复上报与无效流量都不抛异常：媒体侧重试是常态，
 * 把「已经处理过」做成 4xx 会让 SDK 无限重试并触发告警风暴，
 * 正确做法是返回成功并把处置结论写清楚。
 */
@Data
public class AdEventResponse {

    /**
     * 事件幂等键
     */
    private String eventId;

    /**
     * 投放链路 id
     */
    private String traceId;

    /**
     * 事件类型名称
     */
    private String eventType;

    /**
     * 是否被接受为有效事件
     */
    private boolean accepted;

    /**
     * 是否是一次重放
     */
    private boolean duplicate;

    /**
     * 计费状态名称
     */
    private String chargeStatus;

    /**
     * 本笔计费金额，单位分
     */
    private Long chargeCents;

    /**
     * 预算闸门路径
     */
    private String chargeMode;

    /**
     * 处置说明或过滤原因
     */
    private String reason;

    /**
     * 链路诊断信息
     */
    private Map<String, Object> diagnostics = new LinkedHashMap<>();

    /**
     * 构造重放响应。
     *
     * @param eventId 事件幂等键
     * @param traceId 链路 id
     * @param type    事件类型
     * @return 响应对象
     */
    public static AdEventResponse replayed(String eventId, String traceId, AdEventType type) {
        AdEventResponse response = base(eventId, traceId, type);
        response.setDuplicate(true);
        response.setReason("duplicate event replayed");
        return response;
    }

    /**
     * 构造被过滤响应。
     *
     * @param eventId 事件幂等键
     * @param traceId 链路 id
     * @param type    事件类型
     * @param reason  过滤原因
     * @return 响应对象
     */
    public static AdEventResponse filtered(String eventId, String traceId, AdEventType type, String reason) {
        AdEventResponse response = base(eventId, traceId, type);
        response.setReason(reason);
        response.setChargeStatus(AdChargeStatus.NONE.name());
        return response;
    }

    /**
     * 构造已接受响应。
     *
     * @param eventId      事件幂等键
     * @param traceId      链路 id
     * @param type         事件类型
     * @param chargeCents  计费金额，单位分
     * @param chargeMode   预算闸门路径
     * @param chargeStatus 计费状态
     * @return 响应对象
     */
    public static AdEventResponse accepted(String eventId, String traceId, AdEventType type, long chargeCents, String chargeMode, AdChargeStatus chargeStatus) {
        AdEventResponse response = base(eventId, traceId, type);
        response.setAccepted(true);
        response.setChargeCents(chargeCents);
        response.setChargeMode(chargeMode);
        response.setChargeStatus(chargeStatus.name());
        response.setReason("accepted");
        return response;
    }

    /**
     * 构造基础响应。
     *
     * @param eventId 事件幂等键
     * @param traceId 链路 id
     * @param type    事件类型
     * @return 响应对象
     */
    private static AdEventResponse base(String eventId, String traceId, AdEventType type) {
        AdEventResponse response = new AdEventResponse();
        response.setEventId(eventId);
        response.setTraceId(traceId);
        response.setEventType(type.name());
        response.setChargeCents(0L);
        return response;
    }

}
