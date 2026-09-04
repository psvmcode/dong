package com.dong.lab.tcc.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * TCC 库存。在 Try-Confirm-Cancel 分布式事务中承担库存的预留与扣减，
 * available 与 frozen 配合实现资源隔离。
 */
@Data

public class TccInventory {

    /**
     * 主键
     */
    private Long id;

    /**
     * 商品 id
     */
    private Long productId;

    /**
     * 可用库存
     */
    private Integer available;

    /**
     * 冻结库存，Try 冻结 Confirm 扣减 Cancel 释放
     */
    private Integer frozen;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
