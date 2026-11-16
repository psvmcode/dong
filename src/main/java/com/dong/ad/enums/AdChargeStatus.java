package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 计费状态。ad_event 与 ad_charge_record 共用同一套编码，
 * 前者回答「这笔事件走到哪一步」，后者回答「这笔钱最终算不算数」。
 *
 * <p>PENDING 是消息丢失的检测点：超过阈值仍停在这里，
 * 说明异步计费没跑完，对账任务会按同一 eventId 重投，靠唯一索引保证不重复扣钱。
 */
@Getter
@AllArgsConstructor
public enum AdChargeStatus {

    /**
     * 无需计费，例如 CPC 计划的曝光事件。
     */
    NONE(0),

    /**
     * 待计费，已投递或待投递消息。
     */
    PENDING(1),

    /**
     * 已计费。
     */
    CHARGED(2),

    /**
     * 已拒绝，预算或账户余额不足。
     */
    REJECTED(3),

    /**
     * 死信，补投超过上限仍未成功。不再自动重投，等人工介入或归档任务处理。
     */
    DEAD(4);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取计费状态枚举。
     *
     * @param code 状态编码
     * @return 计费状态枚举
     */
    public static AdChargeStatus of(int code) {
        for (AdChargeStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad charge status " + code);
    }

}
