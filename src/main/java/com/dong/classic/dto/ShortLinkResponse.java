package com.dong.classic.dto;

import com.dong.classic.entity.ShortLink;

import java.time.LocalDateTime;

/**
 * 短链接响应 DTO。
 */
public class ShortLinkResponse {

    /**
     * 短码。
     */
    private String code;

    /**
     * 原始链接。
     */
    private String originUrl;

    /**
     * 点击次数。
     */
    private Long hitCount;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换为 DTO。
     *
     * @param shortLink 短链接实体
     * @return 短链接响应 DTO
     */
    public static ShortLinkResponse from(ShortLink shortLink) {
        ShortLinkResponse response = new ShortLinkResponse();
        response.setCode(shortLink.getCode());
        response.setOriginUrl(shortLink.getOriginUrl());
        response.setHitCount(shortLink.getHitCount());
        response.setCreateTime(shortLink.getCreateTime());
        return response;
    }

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

    /**
     * 获取原始链接。
     *
     * @return 原始链接
     */
    public String getOriginUrl() {
        return originUrl;
    }

    /**
     * 设置原始链接。
     *
     * @param originUrl 原始链接
     */
    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    /**
     * 获取点击次数。
     *
     * @return 点击次数
     */
    public Long getHitCount() {
        return hitCount;
    }

    /**
     * 设置点击次数。
     *
     * @param hitCount 点击次数
     */
    public void setHitCount(Long hitCount) {
        this.hitCount = hitCount;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
