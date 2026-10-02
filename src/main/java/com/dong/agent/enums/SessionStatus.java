package com.dong.agent.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 会话状态。只有活跃与归档两种。
 *
 * <p>会话不承载流程：一次执行的状态在 RunStatus 里，两者分开，
 * 才不会出现「会话已结束但运行还在跑」这种说不清楚的中间态。
 */
@Getter
@AllArgsConstructor
public enum SessionStatus {

    /**
     * 活跃，可以继续追问。
     */
    ACTIVE(1),

    /**
     * 归档，仍可回放历史，但不再接受新的运行。
     */
    ARCHIVED(2);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取会话状态枚举。
     *
     * @param code 状态编码
     * @return 会话状态枚举
     */
    public static SessionStatus of(int code) {
        for (SessionStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown session status " + code);
    }

}
