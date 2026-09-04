package com.dong.lab.mq.mapper;

import com.dong.lab.mq.entity.MqMessageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * MqMessageLogMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface MqMessageLogMapper {

    /**
     * 插入记录，返回影响行数。
     */
    int insert(MqMessageLog log);

    /**
     * countByMsgId。
     */
    int countByMsgId(@Param("msgId") String msgId);

    /**
     * 增加重试次数。
     */
    int increaseRetry(@Param("msgId") String msgId);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("msgId") String msgId, @Param("status") int status);

    /**
     * 查询最近的记录。
     */
    List<MqMessageLog> selectRecent(@Param("limit") int limit);

    /**
     * 统计所有记录数。
     */
    long countAll();

}
