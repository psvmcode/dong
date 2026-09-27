package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 实验记录查询基类。发号器、锁、限流三组记录的差别只在定位键，
 * 条数限制与默认值完全一致，因此抽到基类里。
 */
public class LabRecordQueryRequest {

    /**
     * 返回条数。
     */
    @Min(1)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int limit = 10;

    /**
     * 获取返回条数。
     *
     * @return 返回条数
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置返回条数。
     *
     * @param limit 返回条数
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

}
