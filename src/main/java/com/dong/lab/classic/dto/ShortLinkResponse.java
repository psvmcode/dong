package com.dong.lab.classic.dto;

import com.dong.lab.classic.entity.ShortLink;

import java.time.LocalDateTime;

/**
 * ShortLinkResponse。
 */
public class ShortLinkResponse {

    /**
     * 编码。
     */
    private String code;

    /**
     * originUrl。
     */
    private String originUrl;

    /**
     * hitCount。
     */
    private Long hitCount;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换为 DTO。
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
     * getCode。
     */
    public String getCode() {
        return code;
    }

    /**
     * setCode。
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * getOriginUrl。
     */
    public String getOriginUrl() {
        return originUrl;
    }

    /**
     * setOriginUrl。
     */
    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    /**
     * getHitCount。
     */
    public Long getHitCount() {
        return hitCount;
    }

    /**
     * setHitCount。
     */
    public void setHitCount(Long hitCount) {
        this.hitCount = hitCount;
    }

    /**
     * getCreateTime。
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * setCreateTime。
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
