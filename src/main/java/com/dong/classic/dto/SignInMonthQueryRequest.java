package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.YearMonth;

/**
 * 按月签到查询请求。月份不传表示查当月，格式 yyyy-MM。
 */
public class SignInMonthQueryRequest {

    /**
     * 用户标识。
     */
    @NotBlank
    @Size(max = 128)
    private String userId;

    /**
     * 查询月份，不传表示当月。
     */
    private YearMonth month;

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
     * 获取查询月份。
     *
     * @return 查询月份
     */
    public YearMonth getMonth() {
        return month;
    }

    /**
     * 设置查询月份。
     *
     * @param month 查询月份
     */
    public void setMonth(YearMonth month) {
        this.month = month;
    }

}
