package com.dong.agent.mapper;

import com.dong.agent.entity.AgentSession;
import com.dong.agent.enums.SessionStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 会话数据访问层。
 */
@Mapper
public interface AgentSessionMapper {

    /**
     * 插入会话，主键回填。
     *
     * @param session 会话实体
     * @return 影响行数
     */
    int insert(AgentSession session);

    /**
     * 按会话号查询。
     *
     * @param sessionNo 会话号
     * @return 会话实体，不存在时返回 null
     */
    AgentSession selectBySessionNo(@Param("sessionNo") String sessionNo);

    /**
     * 分页查询会话，按最近更新排序。
     *
     * @param offset 偏移量
     * @param size   条数
     * @return 会话列表
     */
    List<AgentSession> selectByPage(@Param("offset") int offset, @Param("size") int size);

    /**
     * 统计会话总数。
     *
     * @return 会话总数
     */
    long countAll();

    /**
     * 累加消息数与运行数。用累加而不是直接赋值，
     * 避免并发运行互相覆盖计数。
     *
     * @param sessionNo   会话号
     * @param messageDelta 消息增量
     * @param runDelta     运行增量
     * @return 影响行数
     */
    int increaseCounters(@Param("sessionNo") String sessionNo, @Param("messageDelta") int messageDelta, @Param("runDelta") int runDelta);

    /**
     * 更新会话摘要。
     *
     * @param sessionNo 会话号
     * @param summary   摘要内容
     * @return 影响行数
     */
    int updateSummary(@Param("sessionNo") String sessionNo, @Param("summary") String summary);

    /**
     * 更新会话标题，首轮运行结束后补上。
     *
     * @param sessionNo 会话号
     * @param title     标题
     * @return 影响行数
     */
    int updateTitle(@Param("sessionNo") String sessionNo, @Param("title") String title);

    /**
     * 更新会话状态。
     *
     * @param sessionNo 会话号
     * @param status    目标状态
     * @return 影响行数
     */
    int updateStatus(@Param("sessionNo") String sessionNo, @Param("status") SessionStatus status);

    /**
     * 删除会话。
     *
     * @param sessionNo 会话号
     * @return 影响行数
     */
    int deleteBySessionNo(@Param("sessionNo") String sessionNo);

}
