package com.dong.lab.cache.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ProductStatus {

    ON_SALE(1),

    OFF_SHELF(2);

    private final int code;

    public static ProductStatus of(int code) {
        for (ProductStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown product status " + code);
    }

}
