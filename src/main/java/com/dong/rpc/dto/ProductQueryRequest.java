package com.dong.rpc.dto;

import com.dong.rpc.annotation.ProtoField;
import com.dong.rpc.annotation.ProtoMessage;
import com.dong.rpc.codec.ProtoType;
import lombok.Data;

/**
 * 商品查询请求。
 */
@Data
@ProtoMessage
public class ProductQueryRequest {

    /**
     * 关键字，为空表示不过滤。
     */
    @ProtoField(order = 1, type = ProtoType.STRING)
    private String keyword;

    /**
     * 页码，从 1 开始。
     */
    @ProtoField(order = 2, type = ProtoType.INT32)
    private int pageNum = 1;

    /**
     * 每页条数。
     */
    @ProtoField(order = 3, type = ProtoType.INT32)
    private int pageSize = 10;

}
