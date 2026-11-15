package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建广告单元请求。四个定向维度都可以不传，不传即不限。
 */
@Data
public class AdUnitCreateRequest {

    /**
     * 所属计划 id。
     */
    @NotNull
    @Positive
    private Long campaignId;

    /**
     * 单元名称。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String name;

    /**
     * 地域定向。
     */
    @Size(max = Constants.MAX_TARGETING_ITEMS)
    private List<String> regions;

    /**
     * 人群标签定向。
     */
    @Size(max = Constants.MAX_TARGETING_ITEMS)
    private List<String> tags;

    /**
     * 设备定向。
     */
    @Size(max = Constants.MAX_TARGETING_ITEMS)
    private List<String> devices;

    /**
     * 关键词定向。
     */
    @Size(max = Constants.MAX_TARGETING_ITEMS)
    private List<String> keywords;

    /**
     * 计费类型编码，1 CPC 2 CPM。
     */
    @NotNull
    @Min(1)
    @Max(2)
    private Integer bidType;

    /**
     * 出价，单位分。
     */
    @NotNull
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long bidCents;

    /**
     * 保留价，单位分，不传取 1 分。
     */
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long floorCents;

    /**
     * 预估点击率，万分比，范围 1 到 10000。
     */
    @NotNull
    @Min(1)
    @Max(10_000)
    private Integer pctr;

}
