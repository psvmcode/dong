package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 独立访客区间查询请求。合并多个 HyperLogLog 得到去重结果，
 * 而不是把每天的估算值相加，否则同一个人会被重复计数。
 */
public class UvRangeQueryRequest {

    /**
     * 页面标识。
     */
    @NotBlank
    @Size(max = 128)
    private String page = "home";

    /**
     * 起始日期，含当天。
     */
    private LocalDate from;

    /**
     * 结束日期，含当天。
     */
    private LocalDate to;

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
     * 获取起始日期。
     *
     * @return 起始日期
     */
    public LocalDate getFrom() {
        return from;
    }

    /**
     * 设置起始日期。
     *
     * @param from 起始日期
     */
    public void setFrom(LocalDate from) {
        this.from = from;
    }

    /**
     * 获取结束日期。
     *
     * @return 结束日期
     */
    public LocalDate getTo() {
        return to;
    }

    /**
     * 设置结束日期。
     *
     * @param to 结束日期
     */
    public void setTo(LocalDate to) {
        this.to = to;
    }

}
