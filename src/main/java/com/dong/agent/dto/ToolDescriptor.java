package com.dong.agent.dto;

import lombok.Data;

/**
 * 工具描述。给接口层用，页面据此展示工具清单与危险等级。
 */
@Data
public class ToolDescriptor {

    /**
     * 工具名
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 入参的 JSON Schema
     */
    private String parametersSchema;

    /**
     * 危险等级：READ_ONLY SIDE_EFFECT
     */
    private String risk;

    /**
     * 是否需要用户确认
     */
    private Boolean needConfirm;

    /**
     * 超时秒数
     */
    private Long timeoutSeconds;

}
