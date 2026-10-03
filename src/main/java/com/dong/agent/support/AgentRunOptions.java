package com.dong.agent.support;

/**
 * 实验参数覆盖。默认全为 null，表示沿用配置里的值；
 * 只有跑对照实验时才会带上具体值。
 *
 * <p>单独抽成 record 而不是往引擎里塞一堆开关字段，
 * 是为了让「正常跑」与「跑实验」走完全相同的代码路径：
 * 两种模式只在参数上不同，比出来的才是调度与策略的差异，不是代码分支的差异。
 *
 * @param toolInject             工具注入方式：full 全部注入，topk 只带最相关的几个
 * @param failureMode            工具失败的处理：visible 原文回传，swallow 吞掉失败
 * @param parallelToolCalls      一轮多个工具调用是否并行
 * @param selfVerify             结束后是否追加一轮自我校验
 * @param maxSteps               步数上限覆盖
 * @param maxToolCalls           工具调用次数上限覆盖
 * @param tokenBudget            token 预算覆盖
 * @param maxContinuousFailures  连续失败上限覆盖
 * @param maxSameToolCalls       同一工具相同参数的重复次数上限覆盖
 */
public record AgentRunOptions(String toolInject, String failureMode, Boolean parallelToolCalls,
                              Boolean selfVerify, Integer maxSteps, Integer maxToolCalls,
                              Integer tokenBudget, Integer maxContinuousFailures, Integer maxSameToolCalls) {

    /**
     * 构造一个「不覆盖任何配置」的选项。
     *
     * @return 空覆盖选项
     */
    public static AgentRunOptions empty() {
        return new AgentRunOptions(null, null, null, null, null, null, null, null, null);
    }

}
