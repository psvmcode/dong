package com.dong.lab.mq.service;

import com.dong.lab.mq.entity.MqMessageLog;

import java.util.List;
import java.util.Map;

public interface MqConsumeService {

    boolean consume(String topic, String msgId, String payload);

    List<MqMessageLog> recent(int limit);

    Map<String, Object> stats();

}
