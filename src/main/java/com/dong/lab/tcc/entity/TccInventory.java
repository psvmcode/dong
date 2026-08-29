package com.dong.lab.tcc.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccInventory {

    private Long id;

    private Long productId;

    private Integer available;

    private Integer frozen;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
