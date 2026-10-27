package com.dong.rpc.dto;

import com.dong.rpc.annotation.ProtoField;
import com.dong.rpc.annotation.ProtoMessage;
import com.dong.rpc.codec.ProtoType;
import lombok.Data;

import java.util.List;

/**
 * 商品查询结果。
 *
 * <p>带重复字段与嵌套消息，是为了让序列化对比有意义：
 * 只拿一个字符串比 protobuf 与 JSON 的体积，比出来的差距没有代表性。
 */
@Data
@ProtoMessage
public class ProductQueryResponse {

    /**
     * 命中总数。
     */
    @ProtoField(order = 1, type = ProtoType.INT64)
    private long total;

    /**
     * 当前页的商品。
     */
    @ProtoField(order = 2, type = ProtoType.MESSAGE, repeated = true)
    private List<ProductItem> items;

}
