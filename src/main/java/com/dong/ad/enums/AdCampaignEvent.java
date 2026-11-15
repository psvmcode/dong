package com.dong.ad.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 推广计划生命周期事件。事件本身不落库，编码只用于接口传参与日志可读性。
 */
@Getter
@AllArgsConstructor
public enum AdCampaignEvent {

    /**
     * 提交审核。
     */
    SUBMIT(1),

    /**
     * 审核通过。
     */
    APPROVE(2),

    /**
     * 审核驳回。
     */
    REJECT(3),

    /**
     * 开始投放。
     */
    START(4),

    /**
     * 暂停投放。
     */
    PAUSE(5),

    /**
     * 恢复投放。
     */
    RESUME(6),

    /**
     * 结束投放。
     */
    FINISH(7);

    /**
     * 事件编码。
     */
    private final int code;

    /**
     * 按名称解析事件，非法名称转成 1000 而不是让接口抛 500。
     *
     * @param name 事件名称
     * @return 事件枚举
     */
    public static AdCampaignEvent ofName(String name) {
        for (AdCampaignEvent event : values()) {
            if (event.name().equalsIgnoreCase(name)) {
                return event;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad campaign event " + name);
    }

    /**
     * 根据编码获取事件枚举。
     *
     * @param code 事件编码
     * @return 事件枚举
     */
    public static AdCampaignEvent of(int code) {
        for (AdCampaignEvent event : values()) {
            if (event.getCode() == code) {
                return event;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown ad campaign event " + code);
    }

}
