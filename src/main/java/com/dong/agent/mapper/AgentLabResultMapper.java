package com.dong.agent.mapper;

import com.dong.agent.entity.AgentLabResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 对照实验结果数据访问层。
 */
@Mapper
public interface AgentLabResultMapper {

    /**
     * 插入实验结果。
     *
     * @param result 实验结果
     * @return 影响行数
     */
    int insert(AgentLabResult result);

    /**
     * 按实验编号查询最近的结果。
     *
     * @param experiment 实验编号
     * @param limit      条数
     * @return 结果列表
     */
    List<AgentLabResult> selectByExperiment(@Param("experiment") String experiment, @Param("limit") int limit);

    /**
     * 查询最近的全部实验结果。
     *
     * @param limit 条数
     * @return 结果列表
     */
    List<AgentLabResult> selectRecent(@Param("limit") int limit);

}
