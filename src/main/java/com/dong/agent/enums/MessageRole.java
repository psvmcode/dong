package com.dong.agent.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息角色。与 OpenAI 协议的角色名一一对应，
 * 存字符串而不是编码，回放时直接可读。
 */
@Getter
@AllArgsConstructor
public enum MessageRole {

    /**
     * 系统提示，约束模型的行为边界。
     */
    SYSTEM("system"),

    /**
     * 用户输入。
     */
    USER("user"),

    /**
     * 模型输出，可能同时带正文与工具调用。
     */
    ASSISTANT("assistant"),

    /**
     * 工具结果，成功与失败都记，是模型自我纠错的依据。
     */
    TOOL("tool");

    /**
     * 协议里的角色名。
     */
    private final String role;

    /**
     * 根据角色名获取枚举。
     *
     * @param role 角色名
     * @return 消息角色枚举
     */
    public static MessageRole of(String role) {
        for (MessageRole item : values()) {
            if (item.getRole().equals(role)) {
                return item;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown message role " + role);
    }

}
