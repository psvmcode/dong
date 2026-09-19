package com.dong.redpacket.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 红包份额状态。份额一旦被领取就不可再分配，
 * 这个状态是防超发的最后一道防线，先于它生效的是 Redis 队列的原子弹出。
 */
@Getter
@AllArgsConstructor
public enum RedPacketItemStatus {

    /**
     * 未领取，仍在待发队列中。
     */
    UNCLAIMED(0),

    /**
     * 已领取，已与某个用户绑定。
     */
    CLAIMED(1);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取份额状态枚举。
     *
     * @param code 状态编码
     * @return 份额状态枚举
     */
    public static RedPacketItemStatus of(int code) {
        for (RedPacketItemStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown red packet item status " + code);
    }

}
