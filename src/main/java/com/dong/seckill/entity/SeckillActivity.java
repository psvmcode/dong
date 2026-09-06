package com.dong.seckill.entity;

import com.dong.seckill.enums.SeckillActivityStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 秒杀活动。记录秒杀商品的库存、价格与时间窗口，
 * 是 Redis 预减库存、异步落库与限流等实验的载体。
 */
@Data

public class SeckillActivity {

    /**
     * 主键
     */
    private Long id;

    /**
     * 参与秒杀的商品 id
     */
    private Long productId;

    /**
     * 活动标题
     */
    private String title;

    /**
     * 活动总库存
     */
    private Integer totalStock;

    /**
     * 剩余库存，Redis 扣减完成后由落库任务回写
     */
    private Integer availableStock;

    /**
     * 秒杀单价
     */
    private BigDecimal unitPrice;

    /**
     * 活动开始时间
     */
    private LocalDateTime startTime;

    /**
     * 活动结束时间
     */
    private LocalDateTime endTime;

    /**
     * 活动状态
     */
    private SeckillActivityStatus status;

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
