package com.dong.agent.support.tool;

/**
 * 工具执行结果。
 *
 * <p>success 为 false 时 errorMessage 必填，内容会原样回传给模型——
 * 这是模型自我纠错的唯一依据。把失败吞掉（返回空内容）的后果是：
 * 模型会把「没查到」理解成「查到了，就是空的」，然后一本正经地编答案。
 *
 * @param success       是否成功
 * @param payload       结果内容，失败时为空
 * @param errorMessage  失败原因，成功时为空
 * @param elapsedMillis 耗时，单位毫秒
 */
public record ToolResult(boolean success, String payload, String errorMessage, long elapsedMillis) {

    /**
     * 构造成功结果。
     *
     * @param payload       结果内容
     * @param elapsedMillis 耗时
     * @return 成功结果
     */
    public static ToolResult ok(String payload, long elapsedMillis) {
        return new ToolResult(true, payload, "", elapsedMillis);
    }

    /**
     * 构造失败结果。
     *
     * @param errorMessage  失败原因
     * @param elapsedMillis 耗时
     * @return 失败结果
     */
    public static ToolResult fail(String errorMessage, long elapsedMillis) {
        return new ToolResult(false, "", errorMessage, elapsedMillis);
    }

}
