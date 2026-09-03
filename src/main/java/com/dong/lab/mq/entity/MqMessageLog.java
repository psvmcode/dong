package com.dong.lab.mq.entity;

import com.dong.lab.mq.enums.MqMessageStatus;
import lombok.Data;

import java.time.LocalDateTime;

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
