package com.dong.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具试运行请求。工具名带点号，所以走 body 而不是路径变量。
 */
@Data
public class ToolDryRunRequest {

    /**
     * 工具名。
     */
    @NotBlank
    @Size(max = 64)
    private String toolName;

    /**
     * 入参，按工具的 schema 填写。
     */
    private Map<String, Object> arguments = new HashMap<>();

}
