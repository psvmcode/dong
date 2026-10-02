package com.dong.agent.support;

import com.dong.agent.enums.FinishReason;

/**
 * 运行结局。一次运行无论怎么结束都必须给出它，
 * 页面据此显示结束原因，统计据此分析闸门是否太紧。
 *
 * @param runNo             运行号
 * @param sessionNo         会话号
 * @param answer            最终回答，被闸门终止时可能为空
 * @param finishReason      结束原因
 * @param steps             实际步数
 * @param toolCalls         工具调用次数
 * @param promptTokens      提示 token
 * @param completionTokens  生成 token
 * @param elapsedMillis     总耗时
 * @param errorMessage      失败原因，正常时为空
 */
public record RunOutcome(String runNo, String sessionNo, String answer, FinishReason finishReason,
                         int steps, int toolCalls, int promptTokens, int completionTokens,
                         long elapsedMillis, String errorMessage) {

    /**
     * 构造成功或被闸门终止的结局。
     *
     * @param runNo            运行号
     * @param sessionNo        会话号
     * @param answer           最终回答
     * @param finishReason     结束原因
     * @param steps            实际步数
     * @param toolCalls        工具调用次数
     * @param promptTokens     提示 token
     * @param completionTokens 生成 token
     * @param elapsedMillis    总耗时
     * @return 运行结局
     */
    public static RunOutcome of(String runNo, String sessionNo, String answer, FinishReason finishReason,
                                int steps, int toolCalls, int promptTokens, int completionTokens, long elapsedMillis) {
        return new RunOutcome(runNo, sessionNo, answer, finishReason, steps, toolCalls,
                promptTokens, completionTokens, elapsedMillis, "");
    }

}
