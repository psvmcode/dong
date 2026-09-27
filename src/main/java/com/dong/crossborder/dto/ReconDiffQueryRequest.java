package com.dong.crossborder.dto;

import jakarta.validation.constraints.Size;

/**
 * 对账差异查询请求。批次号是可选条件，不传表示查最近的全部差异。
 */
public class ReconDiffQueryRequest {

    /**
     * 清算批次号，可选。
     */
    @Size(max = 128)
    private String batchNo;

    /**
     * 获取清算批次号。
     *
     * @return 批次号
     */
    public String getBatchNo() {
        return batchNo;
    }

    /**
     * 设置清算批次号。
     *
     * @param batchNo 批次号
     */
    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

}
