package com.dong.lab.doc.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 操作日志写入请求。
 */
public class OperationLogRequest {

    /**
     * 业务类型。
     */
    @NotBlank
    private String bizType;

    /**
     * 业务单号。
     */
    @NotBlank
    private String bizId;

    /**
     * 操作人。
     */
    private String operator;

    /**
     * 操作动作。
     */
    private String action;

    /**
     * 操作详情，可存放任意结构。
     */
    private Map<String, Object> detail;

    /**
     * 获取业务类型。
     *
     * @return 业务类型
     */
    public String getBizType() {
        return bizType;
    }

    /**
     * 设置业务类型。
     *
     * @param bizType 业务类型
     */
    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    /**
     * 获取业务单号。
     *
     * @return 业务单号
     */
    public String getBizId() {
        return bizId;
    }

    /**
     * 设置业务单号。
     *
     * @param bizId 业务单号
     */
    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    /**
     * 获取操作人。
     *
     * @return 操作人
     */
    public String getOperator() {
        return operator;
    }

    /**
     * 设置操作人。
     *
     * @param operator 操作人
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * 获取操作动作。
     *
     * @return 操作动作
     */
    public String getAction() {
        return action;
    }

    /**
     * 设置操作动作。
     *
     * @param action 操作动作
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * 获取操作详情。
     *
     * @return 操作详情
     */
    public Map<String, Object> getDetail() {
        return detail;
    }

    /**
     * 设置操作详情。
     *
     * @param detail 操作详情
     */
    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }

}
