package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 独立访客查询请求。日期不传表示查当天，格式 yyyy-MM-dd。
 */
public class UvCountQueryRequest {

    /**
     * 页面标识。
     */
    @NotBlank
    @Size(max = 128)
    private String page = "home";

    /**
     * 统计日期，不传表示当天。
     */
    private LocalDate date;

    /**
     * 获取页面标识。
     *
     * @return 页面标识
     */
    public String getPage() {
        return page;
    }

    /**
     * 设置页面标识。
     *
     * @param page 页面标识
     */
    public void setPage(String page) {
        this.page = page;
    }

    /**
     * 获取统计日期。
     *
     * @return 统计日期
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * 设置统计日期。
     *
     * @param date 统计日期
     */
    public void setDate(LocalDate date) {
        this.date = date;
    }

}
