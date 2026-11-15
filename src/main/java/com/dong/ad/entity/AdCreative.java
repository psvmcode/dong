package com.dong.ad.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告创意。审核状态单独存，未过审创意不参与召回，
 * 这样驳回素材不需要删除记录，复审与追责都有据可查。
 */
@Data
public class AdCreative {

    /**
     * 主键
     */
    private Long id;

    /**
     * 创意编号
     */
    private String creativeNo;

    /**
     * 所属广告单元 id
     */
    private Long unitId;

    /**
     * 创意标题
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
     * 审核状态，与 AdCreativeStatus 编码对齐
     */
    private Integer status;

    /**
     * 驳回原因
     */
    private String rejectReason;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
