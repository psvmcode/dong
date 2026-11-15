package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推广计划生命周期状态。
 *
 * <p>迁移表放在枚举里而不是另起状态机：本场景的迁移是线性的、无并行分支，
 * 引入状态机框架只会多一层间接。真正的并发安全不在这里，
 * 而在落库时的 {@code where status = 期望状态 and version = 当前版本} 条件更新。
 *
 * <p>REJECTED 允许重新 SUBMIT：广告主改完素材再提审是日常操作，
 * 把驳回做成终态会逼着运营重新建一个计划，历史数据也断了。
 */
@Getter
@AllArgsConstructor
public enum AdCampaignStatus {

    /**
     * 草稿，仅后台可见。
     */
    DRAFT(0),

    /**
     * 待审核。
     */
    PENDING_REVIEW(1),

    /**
     * 审核通过，尚未开始投放。
     */
    APPROVED(2),

    /**
     * 投放中，参与召回与竞价。
     */
    DELIVERING(3),

    /**
     * 已暂停，保留预算与消耗数据。
     */
    PAUSED(4),

    /**
     * 审核驳回。
     */
    REJECTED(5),

    /**
     * 已结束，终态。
     */
    FINISHED(6);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 计算某事件触发后的目标状态，非法迁移返回 null 由调用方转成 1002。
     *
     * @param event 生命周期事件
     * @return 目标状态，非法迁移为 null
     */
    public AdCampaignStatus next(AdCampaignEvent event) {
        return switch (event) {
            case SUBMIT -> (this == DRAFT || this == REJECTED) ? PENDING_REVIEW : null;
            case APPROVE -> this == PENDING_REVIEW ? APPROVED : null;
            case REJECT -> this == PENDING_REVIEW ? REJECTED : null;
            case START -> this == APPROVED ? DELIVERING : null;
            case PAUSE -> this == DELIVERING ? PAUSED : null;
            case RESUME -> this == PAUSED ? DELIVERING : null;
            case FINISH -> (this == APPROVED || this == DELIVERING || this == PAUSED) ? FINISHED : null;
        };
    }

    /**
     * 根据编码获取状态枚举。
     *
     * @param code 状态编码
     * @return 状态枚举
     */
    public static AdCampaignStatus of(int code) {
        for (AdCampaignStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad campaign status " + code);
    }

}
