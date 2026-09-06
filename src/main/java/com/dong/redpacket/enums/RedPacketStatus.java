package com.dong.redpacket.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 红包状态。描述红包从创建到结束的完整生命周期。
 */
@Getter
@AllArgsConstructor

public enum RedPacketStatus {

    /**
     * 已创建，红包尚未开始发放。
     */
    CREATED(0),

    /**
     * 发放中，红包已被领取过但仍有剩余金额。
     */
    DISTRIBUTING(1),

    /**
     * 已领完，红包金额已被全部领取。
     */
    FINISHED(2),

    /**
     * 已过期，超过领取有效期后剩余金额退回。
     */
    EXPIRED(3);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取红包状态枚举。
     *
     * @param code 状态编码
     * @return 红包状态枚举
     */
    public static RedPacketStatus of(int code) {
        for (RedPacketStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown red packet status " + code);
    }

}
