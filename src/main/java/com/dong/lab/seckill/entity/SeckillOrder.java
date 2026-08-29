package com.dong.lab.seckill.entity;

import com.dong.lab.seckill.enums.SeckillOrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillOrder {

    private Long id;

    private String orderNo;

    private Long activityId;

    private Long productId;

    private Long userId;

    private Integer quantity;

    private BigDecimal amount;

    private SeckillOrderStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
