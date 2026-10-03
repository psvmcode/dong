package com.dong.agent.service;

import com.dong.agent.dto.RunListQuery;
import com.dong.agent.dto.RunRequest;
import com.dong.agent.dto.RunResponse;
import com.dong.agent.dto.RunStatsResponse;
import com.dong.agent.support.AgentRunOptions;
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
     * 发起运行并绕过并发闸门。仅供对照实验 E8 使用：
     * 它要比的正是「有没有这道闸门」的区别，所以两条路径都得走得通。
     *
     * @param request 运行请求
     * @return 运行结果
     */
    RunResponse runUngated(RunRequest request);

    /**
     * 带实验参数发起运行。对照实验通过它覆盖引擎参数与历史窗口，
     * 与正常运行走完全相同的代码路径——比出来的才是策略差异，不是实现差异。
     *
     * @param request       运行请求
     * @param options       实验参数覆盖，正常运行时传 null
     * @param historyWindow 历史窗口覆盖，正常运行时传 null
     * @return 运行结果
     */
    RunResponse runWithOptions(RunRequest request, AgentRunOptions options, Integer historyWindow);

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
     * 确认执行挂起的有副作用工具，从挂起处继续。
     *
     * @param runNo 运行号
     * @return 运行结果
     */
    RunResponse confirm(String runNo);

    /**
     * 拒绝执行挂起的工具，把拒绝告知模型让它改道。
     *
     * @param runNo 运行号
     * @return 运行结果
     */
    RunResponse reject(String runNo);

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
