package com.dong.agent.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 运行状态。等待确认是一个真实存在的中间态：
 * 工具需要用户确认时运行必须挂起，且挂起态要落库，
 * 否则进程重启后就找不到该从哪一步继续。
 */
@Getter
@AllArgsConstructor
public enum RunStatus {

    /**
     * 运行中，循环正在推进。
     */
    RUNNING(1),

    /**
     * 已完成，正常结束或被闸门终止都算完成，具体看 finish_reason。
     */
    FINISHED(2),

    /**
     * 失败，引擎自身出错或模型不可用。
     */
    FAILED(3),

    /**
     * 已取消，用户主动停止或确认超时。
     */
    CANCELLED(4),

    /**
     * 等待确认，挂起在有副作用的工具调用上。
     */
    WAITING_CONFIRM(5);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取运行状态枚举。
     *
     * @param code 状态编码
     * @return 运行状态枚举
     */
    public static RunStatus of(int code) {
        for (RunStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown run status " + code);
    }

    /**
     * 判断是否为终态。终态不会再变化，清理任务据此跳过。
     *
     * @return true 表示运行已终结
     */
    public boolean isTerminal() {
        return this == FINISHED || this == FAILED || this == CANCELLED;
    }

}
