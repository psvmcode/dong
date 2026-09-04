package com.dong.lab.mq.entity;

import com.dong.lab.mq.enums.MqMessageStatus;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * MQ 消息消费日志。记录每条消息的消费状态与重试次数，
 * 是幂等消费、死信识别与消费审计的依据。
 */
@Data

public class MqMessageLog {

    /**
     * 主键
     */
    private Long id;

    /**
     * 消息唯一标识，唯一索引实现消费幂等
     */
    private String msgId;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 消息内容
     */
    private String payload;

    /**
     * 消费状态，0 待处理 1 成功
     */
    private MqMessageStatus status;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
