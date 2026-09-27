package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 锁实验记录查询请求。按模式筛选，no-lock 与 with-lock 两组对比着看。
 */
public class LockRecordQueryRequest extends LabRecordQueryRequest {

    /**
     * 实验模式：no-lock、with-lock。
     */
    @NotBlank
    @Size(max = 32)
    private String mode = "no-lock";

    /**
     * 获取实验模式。
     *
     * @return 模式
     */
    public String getMode() {
        return mode;
    }

    /**
     * 设置实验模式。
     *
     * @param mode 模式
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

}
