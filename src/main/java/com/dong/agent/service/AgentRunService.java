package com.dong.agent.service;

import com.dong.agent.dto.RunListQuery;
import com.dong.agent.dto.RunRequest;
import com.dong.agent.dto.RunResponse;
import com.dong.agent.dto.RunStatsResponse;
import com.dong.common.result.PageResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Agent 运行服务。
 *
 * <p>同步与流式两条路径共用同一套循环，区别只在事件出口：
 * 同步只取最终结果，流式逐步推给页面。
 */
public interface AgentRunService {

    /**
     * 发起运行，同步等待结束，返回完整结果。调试与实验用。
     *
     * @param request 运行请求
     * @return 运行结果
     */
    RunResponse run(RunRequest request);

    /**
     * 发起运行，SSE 流式推送过程。页面用这条。
     *
     * @param request 运行请求
     * @return SSE 发射器
     */
    SseEmitter stream(RunRequest request);

    /**
     * 查询运行结果。
     *
     * @param runNo 运行号
     * @return 运行结果
     */
    RunResponse detail(String runNo);

    /**
     * 取消运行。只置标记，由循环下一轮自己停下来。
     *
     * @param runNo 运行号
     */
    void cancel(String runNo);

    /**
     * 分页查询运行。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<RunResponse> list(RunListQuery query);

    /**
     * 运行统计，含各结束原因的分布。
     *
     * @return 统计结果
     */
    RunStatsResponse stats();

}
