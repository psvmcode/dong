package com.dong.rpc.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 跨模块商品查询请求。
 */
@Data
public class RpcProductRequest {

    /**
     * 关键字，为空表示不过滤。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String keyword;

    /**
     * 页码，从 1 开始。
     */
    @Min(1)
    @Max(Constants.MAX_PAGE_NUM)
    private int pageNum = Constants.DEFAULT_PAGE_NUM;

    /**
     * 每页条数。
     */
    @Min(1)
    @Max(Constants.MAX_PAGE_SIZE)
    private int pageSize = Constants.DEFAULT_PAGE_SIZE;

    /**
     * 转成服务入参。
     *
     * @return 查询条件
     */
    public ProductQueryRequest toQuery() {
        ProductQueryRequest request = new ProductQueryRequest();
        request.setKeyword(keyword);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return request;
    }

}
