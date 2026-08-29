package com.dong.lab.mq.mapper;

import com.dong.lab.mq.entity.MqMessageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MqMessageLogMapper {

    int insert(MqMessageLog log);

    int countByMsgId(@Param("msgId") String msgId);

    int increaseRetry(@Param("msgId") String msgId);

    int updateStatus(@Param("msgId") String msgId, @Param("status") int status);

    List<MqMessageLog> selectRecent(@Param("limit") int limit);

    long countAll();

}
