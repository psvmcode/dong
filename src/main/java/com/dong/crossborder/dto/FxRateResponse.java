package com.dong.crossborder.dto;

import com.dong.crossborder.entity.FxRate;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 汇率牌价响应。
 *
 * <p>usdRate 的含义是「一美元兑换多少该币种」，不是「一单位该币种换多少美元」，
 * 这个方向最容易搞反，接口层同样保持与库内一致的口径，避免二次换算引入误差。
 */
@Data

public class FxRateResponse {

    /**
     * 币种代码
     */
    private String currency;

    /**
     * 一美元兑换该币种的数量
     */
    private BigDecimal usdRate;

    /**
     * 状态：1 启用 2 停用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 从实体转换。
     *
     * @param rate 牌价实体
     * @return 响应对象
     */
    public static FxRateResponse from(FxRate rate) {
        FxRateResponse response = new FxRateResponse();
        response.setCurrency(rate.getCurrency());
        response.setUsdRate(rate.getUsdRate());
        response.setStatus(rate.getStatus());
        response.setCreateTime(rate.getCreateTime());
        response.setUpdateTime(rate.getUpdateTime());
        return response;
    }

}
