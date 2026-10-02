package com.dong.agent.mapper;

import com.dong.agent.entity.AgentMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 消息数据访问层。
 */
@Mapper
public interface AgentMessageMapper {

    /**
     * 插入消息。
     *
     * @param message 消息实体
     * @return 影响行数
     */
    int insert(AgentMessage message);

    /**
     * 按会话分页查询，按序号升序，保证可按序回放。
     *
     * @param sessionNo 会话号
     * @param offset    偏移量
     * @param size      条数
     * @return 消息列表
     */
    List<AgentMessage> selectBySession(@Param("sessionNo") String sessionNo, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计会话下的消息数。
     *
     * @param sessionNo 会话号
     * @return 消息数
     */
    long countBySession(@Param("sessionNo") String sessionNo);

    /**
     * 查询会话内当前最大序号，用于续写。
     *
     * @param sessionNo 会话号
     * @return 最大序号，无消息时返回 0
     */
    Integer selectMaxSeq(@Param("sessionNo") String sessionNo);

    /**
     * 按运行号查询全部消息。
     *
     * @param runNo 运行号
     * @return 消息列表
     */
    List<AgentMessage> selectByRunNo(@Param("runNo") String runNo);

    /**
     * 删除会话下的全部消息。
     *
     * @param sessionNo 会话号
     * @return 影响行数
     */
    int deleteBySessionNo(@Param("sessionNo") String sessionNo);

}
