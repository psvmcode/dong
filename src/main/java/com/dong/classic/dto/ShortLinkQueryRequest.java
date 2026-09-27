package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 短链查询请求。解析、详情、点击数三个接口共用，
 * 因为它们的入参都只是一个短码。
 */
public class ShortLinkQueryRequest {

    /**
     * 短码。
     */
    @NotBlank
    @Size(max = 128)
    private String code;

    /**
     * 获取短码。
     *
     * @return 短码
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置短码。
     *
     * @param code 短码
     */
    public void setCode(String code) {
        this.code = code;
    }

}
