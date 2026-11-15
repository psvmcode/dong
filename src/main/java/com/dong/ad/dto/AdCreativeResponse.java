package com.dong.ad.dto;

import com.dong.ad.entity.AdCreative;
import com.dong.ad.enums.AdCreativeStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告创意响应。
 */
@Data
public class AdCreativeResponse {

    /**
     * 主键
     */
    private Long id;

    /**
     * 创意编号
     */
    private String creativeNo;

    /**
     * 单元 id
     */
    private Long unitId;

    /**
     * 标题
     */
    private String title;

    /**
     * 素材地址
     */
    private String imageUrl;

    /**
     * 落地页地址
     */
    private String landingUrl;

    /**
     * 审核状态名称
     */
    private String status;

    /**
     * 驳回原因
     */
    private String rejectReason;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 由实体组装响应。
     *
     * @param entity 创意实体
     * @return 响应对象
     */
    public static AdCreativeResponse from(AdCreative entity) {
        AdCreativeResponse response = new AdCreativeResponse();
        response.setId(entity.getId());
        response.setCreativeNo(entity.getCreativeNo());
        response.setUnitId(entity.getUnitId());
        response.setTitle(entity.getTitle());
        response.setImageUrl(entity.getImageUrl());
        response.setLandingUrl(entity.getLandingUrl());
        response.setStatus(AdCreativeStatus.of(entity.getStatus()).name());
        response.setRejectReason(entity.getRejectReason());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

}
