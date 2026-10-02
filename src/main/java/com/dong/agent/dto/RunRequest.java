package com.dong.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发起运行请求。
 *
 * <p>clientToken 是幂等键：页面断线重连是最常见的重复提交来源，
 * 不带它，一次刷新就会再跑一遍整个循环。
 */
@Data
public class RunRequest {

    /**
     * 用户输入。
     */
    @NotBlank
    @Size(max = 4096)
    private String prompt;

    /**
     * 会话号，留空则自动创建会话。
     */
    @Size(max = 32)
    private String sessionNo;

    /**
     * 幂等键，重放返回原运行号。
     */
    @Size(max = 64)
    private String clientToken;

    /**
     * 是否放行有副作用的工具，默认 false。
     */
    private boolean confirmSideEffect = false;

}
