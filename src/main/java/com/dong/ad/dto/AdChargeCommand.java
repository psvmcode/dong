package com.dong.ad.dto;

/**
 * 计费消息体。必须自包含：异步消费时不再反查投放令牌，
 * 否则令牌行被清理或进程刚重启就会查不到，消息只能进死信。
 *
 * <p>date 用 yyyy-MM-dd 字符串而不是 LocalDate，
 * 避免不同消息中间件的 JSON 序列化对时间类型处理不一致。
 *
 * @param eventId      事件幂等键
 * @param traceId      投放链路 id
 * @param statDate     归属日期，yyyy-MM-dd
 * @param campaignId   计划 id
 * @param unitId       单元 id
 * @param creativeId   创意 id
 * @param advertiserId 广告主 id
 * @param eventType    触发计费的事件类型编码
 * @param bidType      计费类型编码
 * @param amountCents  待扣金额，单位分
 * @param chargeMode   预算闸门路径
 */
public record AdChargeCommand(String eventId, String traceId, String statDate, Long campaignId, Long unitId,
                              Long creativeId, Long advertiserId, Integer eventType, Integer bidType,
                              Long amountCents, String chargeMode) {
}
