package com.dong.classic.mapper;

import com.dong.classic.entity.ClassicDelayTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 延迟任务数据访问。
 */
@Mapper

public interface ClassicDelayTaskMapper {

    /**
     * 新增任务记录，投递前先落库，保证任务有据可查。
     */
    int insert(ClassicDelayTask task);

    /**
     * 推进任务状态，已消费时同时写入实际消费时间。
     */
    int updateStatus(@Param("taskNo") String taskNo, @Param("status") int status,
                     @Param("actualTime") LocalDateTime actualTime);

    /**
     * 递增重投次数。
     */
    int increaseRetry(@Param("taskNo") String taskNo);

    /**
     * 按任务内容标记已消费。队列里只传 payload，
     * 因此按 payload 反查一条尚未消费的记录来更新，取预计时间最早的那条。
     */
    int markConsumedByPayload(@Param("payload") String payload,
                              @Param("actualTime") LocalDateTime actualTime);

    /**
     * 扫描已到预计时间却仍未消费的任务，用于补偿重投。
     */
    List<ClassicDelayTask> selectOverdue(@Param("threshold") LocalDateTime threshold,
                                         @Param("limit") int limit);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
