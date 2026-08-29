package com.dong.lab.classic.dto;

import com.dong.lab.classic.entity.ShortLink;

import java.time.LocalDateTime;

public class ShortLinkResponse {

    private String code;

    private String originUrl;

    private Long hitCount;

    private LocalDateTime createTime;

    public static ShortLinkResponse from(ShortLink shortLink) {
        ShortLinkResponse response = new ShortLinkResponse();
        response.setCode(shortLink.getCode());
        response.setOriginUrl(shortLink.getOriginUrl());
        response.setHitCount(shortLink.getHitCount());
        response.setCreateTime(shortLink.getCreateTime());
        return response;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getOriginUrl() {
        return originUrl;
    }

    public void setOriginUrl(String originUrl) {
        this.originUrl = originUrl;
    }

    public Long getHitCount() {
        return hitCount;
    }

    public void setHitCount(Long hitCount) {
        this.hitCount = hitCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
