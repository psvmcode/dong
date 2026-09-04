package com.dong.lab.mq.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * MQ 消息消费状态。记录消息是被正常消费还是进入死信队列。
 */
@Getter
@AllArgsConstructor

public enum MqMessageStatus {

    /**
     * 已正常消费。
     */
    CONSUMED(0),

    /**
     * 已进入死信队列，消费失败次数超过阈值，需人工介入处理。
     */
    DEAD_LETTERED(1);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取 MQ 消息消费状态枚举。
     *
     * @param code 状态编码
     * @return MQ 消息消费状态枚举
     */
    public static MqMessageStatus of(int code) {
        for (MqMessageStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown mq message status " + code);
    }

}
