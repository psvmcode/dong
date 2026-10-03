package com.dong.agent.mapper;

import com.dong.agent.entity.AgentRun;
import com.dong.agent.enums.FinishReason;
import com.dong.agent.enums.RunStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 运行数据访问层。
 */
@Mapper
public interface AgentRunMapper {

    /**
     * 插入运行记录。
     *
     * @param run 运行实体
     * @return 影响行数
     */
    int insert(AgentRun run);

    /**
     * 按运行号查询。
     *
     * @param runNo 运行号
     * @return 运行实体，不存在时返回 null
     */
    AgentRun selectByRunNo(@Param("runNo") String runNo);

    /**
     * 按幂等键查询，用于重复提交时返回原单。
     *
     * @param clientToken 幂等键
     * @return 运行实体，不存在时返回 null
     */
    AgentRun selectByClientToken(@Param("clientToken") String clientToken);

    /**
     * 分页查询运行，按创建时间倒序。
     *
     * @param offset 偏移量
     * @param size   条数
     * @return 运行列表
     */
    List<AgentRun> selectByPage(@Param("offset") int offset, @Param("size") int size);

    /**
     * 统计运行总数。
     *
     * @return 运行总数
     */
    long countAll();

    /**
     * 收尾更新：状态、结束原因、回答与全部统计值。
     *
     * @param run 运行实体
     * @return 影响行数
     */
    int updateFinish(AgentRun run);

    /**
     * 单独更新状态，取消与挂起用。
     *
     * @param runNo  运行号
     * @param status 目标状态
     * @return 影响行数
     */
    int updateStatus(@Param("runNo") String runNo, @Param("status") RunStatus status);

    /**
     * 查询停留在某个状态超过给定时间的运行。运行中用它找卡死的，
     * 等待确认用它找确认超时的——两者阈值不同但查询形状一样。
     *
     * @param status 运行状态编码
     * @param before 更新时间早于该时间点
     * @param limit  最多返回条数
     * @return 运行列表
     */
    List<AgentRun> selectByStatusBefore(@Param("status") RunStatus status, @Param("before") LocalDateTime before,
                                        @Param("limit") int limit);

    /**
     * 记录挂起等待确认的工具调用。
     *
     * @param runNo        运行号
     * @param pendingCalls 待确认调用 JSON
     * @return 影响行数
     */
    int updatePending(@Param("runNo") String runNo, @Param("pendingCalls") String pendingCalls);

    /**
     * 按结束原因统计分布，用来看闸门是不是太紧。
     *
     * @param finishReason 结束原因
     * @return 该原因的运行数
     */
    long countByFinishReason(@Param("finishReason") FinishReason finishReason);

}
