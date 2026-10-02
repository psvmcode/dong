package com.dong.agent.support.llm;

import java.util.function.Consumer;

/**
 * 模型客户端。业务只依赖这一层，底层可以是真实模型也可以是剧本化的 mock。
 *
 * <p>流式通过 onDelta 回调：拿到一段就推一段。
 * 长回答让用户等满再一次性返回，体验上等于「卡住了」。
 */
public interface LlmClient {

    /**
     * 模型提供方标识，用于区分实验数据的来源。
     *
     * @return 提供方标识，如 mock、openai
     */
    String provider();

    /**
     * 发起一次对话。
     *
     * @param request 请求
     * @param onDelta 流式正文回调，可能为 null（跑实验时不需要推流）
     * @return 模型返回结果
     */
    LlmResult chat(ChatRequest request, Consumer<String> onDelta);

}
