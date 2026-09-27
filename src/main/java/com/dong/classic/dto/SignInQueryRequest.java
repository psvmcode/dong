package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 签到查询请求。日期不传表示查当天，格式 yyyy-MM-dd。
 */
public class SignInQueryRequest {

    /**
     * 用户标识。
     */
    @NotBlank
    @Size(max = 128)
    private String userId;

    /**
     * 查询日期，不传表示当天。
     */
    private LocalDate date;

    /**
     * 获取用户标识。
     *
     * @return 用户标识
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 设置用户标识。
     *
     * @param userId 用户标识
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * 获取查询日期。
     *
     * @return 查询日期
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * 设置查询日期。
     *
     * @param date 查询日期
     */
    public void setDate(LocalDate date) {
        this.date = date;
    }

}
