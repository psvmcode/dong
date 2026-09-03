package com.dong.lab.tcc.entity;

import lombok.Data;

import java.time.LocalDateTime;

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
