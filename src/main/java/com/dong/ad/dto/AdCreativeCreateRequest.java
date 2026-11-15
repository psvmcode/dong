package com.dong.ad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建广告创意请求。新建一律是待审核状态，审核接口单独放行。
 */
@Data
public class AdCreativeCreateRequest {

    /**
     * 所属单元 id。
     */
    @NotNull
    @Positive
    private Long unitId;

    /**
     * 创意标题。
     */
    @NotBlank
    @Size(max = 255)
    private String title;

    /**
     * 素材地址，可选。
     */
    @Size(max = 512)
    private String imageUrl;

    /**
     * 落地页地址。
     */
    @NotBlank
    @Size(max = 512)
    private String landingUrl;

}
