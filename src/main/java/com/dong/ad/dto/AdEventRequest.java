package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 曝光或点击上报请求。
 *
 * <p>eventId 由媒体侧生成并在重试时复用，是幂等的唯一依据；
 * traceId 必须是服务端签发的令牌，成交价不可能由客户端传进来，
 * 否则同一个广告位可以把自己点成一分钱。
 */
@Data
public class AdEventRequest {

    /**
     * 投放链路 id，来自广告请求响应。
     */
    @NotBlank
    @Size(max = 64)
    private String traceId;

    /**
     * 事件幂等键，重试必须复用同一个值。
     */
    @NotBlank
    @Size(max = 64)
    private String eventId;

    /**
     * 触达用户 id，必须与令牌里的用户一致。
     */
    @NotNull
    @Positive
    private Long userId;

    /**
     * 上报 IP，可选。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String ip;

    /**
     * 上报 UA，可选。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String userAgent;

}
