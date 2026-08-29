package com.dong.lab.tcc.entity;

import com.dong.lab.tcc.enums.TccOrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccOrder {

    private Long id;

    private String orderNo;

    private String xid;

    private Long userId;

    private Long productId;

    private Integer quantity;

    private Long amount;

    private TccOrderStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
