package com.dong.lab.mq.entity;

import com.dong.lab.mq.enums.MqMessageStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MqMessageLog {

    private Long id;

    private String msgId;

    private String topic;

    private String payload;

    private MqMessageStatus status;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
