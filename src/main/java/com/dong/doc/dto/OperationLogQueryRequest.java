package com.dong.doc.dto;

import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.Size;

/**
 * 操作日志查询请求。业务类型是可选条件，不传表示查全部类型。
 */
public class OperationLogQueryRequest extends PageQuery {

    /**
     * 业务类型，可选。
     */
    @Size(max = 128)
    private String bizType;

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

}
