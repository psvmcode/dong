package com.dong.lab.order.entity;

import com.dong.lab.order.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 交易订单。状态机驱动的核心载体，status 只能由状态机改写，
 * version 负责并发下的乐观锁，两者分工不同缺一不可。
 */
@Data

public class TradeOrder {

    /**
     * 主键
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 下单用户 id
     */
    private Long userId;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 应付金额
     */
    private BigDecimal payAmount;

    /**
     * 已退金额，退款成功时累加
     */
    private BigDecimal refundAmount;

    /**
     * 订单状态
     */
    private OrderStatus status;

    /**
     * 发起退款前的状态，退款失败时据此退回，0 表示未曾退款
     */
    private Integer refundFrom;

    /**
     * 物流单号，发货时写入，同时也是发货守卫的入参
     */
    private String trackingNo;

    /**
     * 支付流水号，支付时写入
     */
    private String payNo;

    /**
     * 催单次数，内部迁移的演示字段
     */
    private Integer urgeCount;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
