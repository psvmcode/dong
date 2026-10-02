package com.dong.agent.mapper;

import com.dong.agent.entity.AgentToolCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 工具调用数据访问层。
 */
@Mapper
public interface AgentToolCallMapper {

    /**
     * 插入工具调用记录。
     *
     * @param toolCall 工具调用实体
     * @return 影响行数
     */
    int insert(AgentToolCall toolCall);

    /**
     * 按运行号查询工具调用轨迹。
     *
     * @param runNo 运行号
     * @return 工具调用列表
     */
    List<AgentToolCall> selectByRunNo(@Param("runNo") String runNo);

    /**
     * 删除会话下的全部工具调用记录。
     *
     * @param sessionNo 会话号
     * @return 影响行数
     */
    int deleteBySessionNo(@Param("sessionNo") String sessionNo);

}
