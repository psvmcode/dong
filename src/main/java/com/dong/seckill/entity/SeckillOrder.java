package com.dong.seckill.entity;

import com.dong.seckill.enums.SeckillOrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 秒杀订单。记录用户在某场秒杀活动中购买的商品、数量与支付状态，
 * 订单生成依赖 Redis 预减库存结果，后续进入支付超时处理。
 */
@Data

public class SeckillOrder {

    /**
     * 主键
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 秒杀活动 id
     */
    private Long activityId;

    /**
     * 商品 id
     */
    private Long productId;

    /**
     * 购买用户 id
     */
    private Long userId;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单金额，单价乘数量
     */
    private BigDecimal amount;

    /**
     * 订单状态，0 待支付 1 已支付 2 已取消
     */
    private SeckillOrderStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
