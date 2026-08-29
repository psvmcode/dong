package com.dong.lab.seckill.entity;

import com.dong.lab.seckill.enums.SeckillActivityStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillActivity {

    private Long id;

    private Long productId;

    private String title;

    private Integer totalStock;

    private Integer availableStock;

    private BigDecimal unitPrice;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private SeckillActivityStatus status;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
