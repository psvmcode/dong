package com.dong.classic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 限流实验记录查询请求。按业务键筛选，重点关注第二轮的放行数量。
 */
public class LimiterRecordQueryRequest extends LabRecordQueryRequest {

    /**
     * 业务键。
     */
    @NotBlank
    @Size(max = 128)
    private String bizKey = "demo";

    /**
     * 获取业务键。
     *
     * @return 业务键
     */
    public String getBizKey() {
        return bizKey;
    }

    /**
     * 设置业务键。
     *
     * @param bizKey 业务键
     */
    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

}
