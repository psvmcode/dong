package com.dong.lab.cache.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 商品状态。用于控制商品是否可对外展示和销售。
 */
@Getter
@AllArgsConstructor

public enum ProductStatus {

    /**
     * 在售，商品对外展示并可正常下单。
     */
    ON_SALE(1),

    /**
     * 已下架，不再对外销售，已有订单不受影响。
     */
    OFF_SHELF(2);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据状态编码获取对应的商品状态枚举。
     *
     * @param code 状态编码
     * @return 商品状态枚举
     */
    public static ProductStatus of(int code) {
        for (ProductStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown product status " + code);
    }

}
