package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 广告请求（召回）参数。模拟一次媒体侧广告位请求携带的上下文。
 */
@Data
public class DeliveryRequest {

    /**
     * 触达用户 id，频次控制与事件归属校验都靠它。
     */
    @NotNull
    @Positive
    private Long userId;

    /**
     * 广告位标识，可选，仅用于链路诊断。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String placement;

    /**
     * 请求地域，可选。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String region;

    /**
     * 用户人群标签，可选。
     */
    @Size(max = Constants.MAX_TARGETING_ITEMS)
    private List<String> tags;

    /**
     * 设备类型，可选。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String device;

    /**
     * 搜索关键词，可选。
     */
    @Size(max = Constants.MAX_TARGETING_ITEMS)
    private List<String> keywords;

    /**
     * 请求 IP，可选，用于无效流量识别。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String ip;

    /**
     * UA，可选，命中黑名单关键字时整条链路按无效流量处理。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String userAgent;

    /**
     * 最多返回多少个候选，默认 1。调大用于观察竞价排序。
     */
    @Min(1)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int maxCandidates = 1;

}
