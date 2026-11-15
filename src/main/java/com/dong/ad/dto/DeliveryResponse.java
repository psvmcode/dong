package com.dong.ad.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 广告请求响应。胜出广告带 traceId，后续曝光与点击上报都必须带它。
 *
 * <p>没有候选时不报错而是返回 matched=false 与原因：广告位空出来是常态，
 * 把它做成异常会让媒体侧每次填充失败都触发重试风暴。
 *
 * <p>diagnostics 把每一道过滤各刷掉了多少候选记下来，
 * 「为什么这条广告没出来」在投放系统里是最常被问的问题。
 */
@Data
public class DeliveryResponse {

    /**
     * 是否有胜出广告
     */
    private boolean matched;

    /**
     * 投放链路 id，未胜出时为空
     */
    private String traceId;

    /**
     * 计划 id
     */
    private Long campaignId;

    /**
     * 单元 id
     */
    private Long unitId;

    /**
     * 创意 id
     */
    private Long creativeId;

    /**
     * 创意标题
     */
    private String title;

    /**
     * 素材地址
     */
    private String imageUrl;

    /**
     * 落地页地址
     */
    private String landingUrl;

    /**
     * 计费类型名称
     */
    private String bidType;

    /**
     * 原始出价，单位分
     */
    private Long bidCents;

    /**
     * 广义二价成交价，单位分
     */
    private Long clearingCents;

    /**
     * 竞价值
     */
    private Long score;

    /**
     * 参与最终排序的候选数
     */
    private int candidateCount;

    /**
     * 候选明细，按竞价值倒序
     */
    private List<Candidate> candidates;

    /**
     * 令牌过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 未胜出原因
     */
    private String reason;

    /**
     * 链路诊断信息
     */
    private Map<String, Object> diagnostics = new LinkedHashMap<>();

    /**
     * 构造未胜出响应。
     *
     * @param reason      未胜出原因
     * @param diagnostics 链路诊断信息
     * @return 响应对象
     */
    public static DeliveryResponse empty(String reason, Map<String, Object> diagnostics) {
        DeliveryResponse response = new DeliveryResponse();
        response.setMatched(false);
        response.setReason(reason);
        response.setCandidates(List.of());
        response.setDiagnostics(diagnostics);
        return response;
    }

    /**
     * 候选明细值对象。
     *
     * @param campaignId    计划 id
     * @param unitId        单元 id
     * @param creativeId    创意 id
     * @param title         创意标题
     * @param bidType       计费类型名称
     * @param bidCents      出价，单位分
     * @param pctr          预估点击率，万分比
     * @param score         竞价值
     * @param clearingCents 二价成交价，单位分
     */
    public record Candidate(Long campaignId, Long unitId, Long creativeId, String title, String bidType,
                            Long bidCents, Integer pctr, Long score, Long clearingCents) {
    }

}
