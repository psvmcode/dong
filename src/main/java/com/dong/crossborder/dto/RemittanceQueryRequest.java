package com.dong.crossborder.dto;

import com.dong.common.result.PageQuery;
import com.dong.crossborder.enums.RemittanceStatus;

/**
 * 汇款单分页查询请求。状态是可选条件，传具体状态取枚举字面量，不传表示查全部。
 */
public class RemittanceQueryRequest extends PageQuery {

    /**
     * 汇款状态，可选。取值见 RemittanceStatus 的枚举字面量。
     */
    private RemittanceStatus status;

    /**
     * 获取汇款状态。
     *
     * @return 汇款状态
     */
    public RemittanceStatus getStatus() {
        return status;
    }

    /**
     * 设置汇款状态。
     *
     * @param status 汇款状态
     */
    public void setStatus(RemittanceStatus status) {
        this.status = status;
    }

}
