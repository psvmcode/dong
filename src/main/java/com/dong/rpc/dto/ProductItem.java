package com.dong.rpc.dto;

import com.dong.rpc.annotation.ProtoField;
import com.dong.rpc.annotation.ProtoMessage;
import com.dong.rpc.codec.ProtoType;
import lombok.Data;

/**
 * 商品条目。
 */
@Data
@ProtoMessage
public class ProductItem {

    /**
     * 商品 id。
     */
    @ProtoField(order = 1, type = ProtoType.STRING)
    private String id;

    /**
     * 商品名。
     */
    @ProtoField(order = 2, type = ProtoType.STRING)
    private String name;

    /**
     * 分类。
     */
    @ProtoField(order = 3, type = ProtoType.STRING)
    private String category;

    /**
     * 价格。用 double 而不是 BigDecimal：protobuf 没有十进制类型，
     * 金额跨服务传递要么用固定精度的 long（分），要么接受二进制浮点的误差。
     * 这里是演示查询，用 double；真要做资金相关的传输，必须用分为单位的 long。
     */
    @ProtoField(order = 4, type = ProtoType.DOUBLE)
    private double price;

}
