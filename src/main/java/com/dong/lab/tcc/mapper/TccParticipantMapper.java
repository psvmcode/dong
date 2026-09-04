package com.dong.lab.tcc.mapper;

import com.dong.lab.tcc.entity.TccAccount;
import com.dong.lab.tcc.entity.TccInventory;
import com.dong.lab.tcc.entity.TccOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/**
 * TccParticipantMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface TccParticipantMapper {

    /**
     * 查询库存。
     */
    TccInventory selectInventory(@Param("productId") Long productId);

    /**
     * 插入库存记录。
     */
    int insertInventory(TccInventory inventory);

    /**
     * 冻结库存。
     */
    int freezeInventory(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 确认扣减库存。
     */
    int confirmInventory(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 释放冻结库存。
     */
    int cancelInventory(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 查询账户。
     */
    TccAccount selectAccount(@Param("userId") Long userId);

    /**
     * 插入账户记录。
     */
    int insertAccount(TccAccount account);

    /**
     * 冻结账户余额。
     */
    int freezeAccount(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 确认扣减账户余额。
     */
    int confirmAccount(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 释放冻结账户余额。
     */
    int cancelAccount(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 根据事务 id 查询订单。
     */
    TccOrder selectOrderByXid(@Param("xid") String xid);

    /**
     * 插入订单记录。
     */
    int insertOrder(TccOrder order);

    /**
     * 更新订单状态。
     */
    int updateOrderStatus(@Param("xid") String xid, @Param("status") int status);

}
