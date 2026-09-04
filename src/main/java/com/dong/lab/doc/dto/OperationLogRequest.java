package com.dong.lab.doc.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * OperationLogRequest。
 */
public class OperationLogRequest {

    @NotBlank
    /**
     * bizType。
     */
    private String bizType;

    @NotBlank
    /**
     * bizId。
     */
    private String bizId;

    /**
     * operator。
     */
    private String operator;

    /**
     * action。
     */
    private String action;

    /**
     * detail。
     */
    private Map<String, Object> detail;

    /**
     * getBizType。
     */
    public String getBizType() {
        return bizType;
    }

    /**
     * setBizType。
     */
    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    /**
     * getBizId。
     */
    public String getBizId() {
        return bizId;
    }

    /**
     * setBizId。
     */
    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    /**
     * getOperator。
     */
    public String getOperator() {
        return operator;
    }

    /**
     * setOperator。
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * getAction。
     */
    public String getAction() {
        return action;
    }

    /**
     * setAction。
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * getDetail。
     */
    public Map<String, Object> getDetail() {
        return detail;
    }

    /**
     * setDetail。
     */
    public void setDetail(Map<String, Object> detail) {
        this.detail = detail;
    }

}
