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
 * 调整广告单元请求。定向列表不传表示保持不变，传空数组表示清空该维度限制。
 */
@Data
public class AdUnitUpdateRequest {

    /**
     * 单元 id。
     */
    @NotNull
    @Positive
    private Long id;

    /**
     * 单元名称。
     */
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
     * 出价，单位分。
     */
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long bidCents;

    /**
     * 保留价，单位分。
     */
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long floorCents;

    /**
     * 预估点击率，万分比。
     */
    @Min(1)
    @Max(10_000)
    private Integer pctr;

}
