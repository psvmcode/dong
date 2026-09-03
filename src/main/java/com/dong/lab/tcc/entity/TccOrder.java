package com.dong.lab.tcc.entity;

import com.dong.lab.tcc.enums.TccOrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccOrder {

    /**
     * 主键
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 所属全局事务 id
     */
    private String xid;

    /**
     * 下单用户 id
     */
    private Long userId;

    /**
     * 商品 id
     */
    private Long productId;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 订单金额，单位分
     */
    private Long amount;

    /**
     * 订单状态
     */
    private TccOrderStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
