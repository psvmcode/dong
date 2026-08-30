package com.dong.lab.mq.service;

import com.dong.lab.mq.entity.MqMessageLog;

import java.util.List;
import java.util.Map;

/**
 * 消息消费。所有传输实现共用同一套处理逻辑，
 * 幂等靠消息日志的唯一索引保证，重复投递只会入库一次。
 */
public interface MqConsumeService {

    /**
     * 消费一条消息，返回 false 表示是重复投递已被拦截。
     */
    boolean consume(String topic, String msgId, String payload);

    /**
     * 查询最近的投递记录。
     */
    List<MqMessageLog> recent(int limit);

    /**
     * 消费统计，duplicated 计数可验证幂等是否生效。
     */
    Map<String, Object> stats();

}
