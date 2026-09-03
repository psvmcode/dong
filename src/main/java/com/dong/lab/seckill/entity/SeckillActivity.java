package com.dong.lab.seckill.entity;

import com.dong.lab.seckill.enums.SeckillActivityStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
