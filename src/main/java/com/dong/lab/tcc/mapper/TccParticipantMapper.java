package com.dong.lab.tcc.mapper;

import com.dong.lab.tcc.entity.TccAccount;
import com.dong.lab.tcc.entity.TccInventory;
import com.dong.lab.tcc.entity.TccOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TccParticipantMapper {

    TccInventory selectInventory(@Param("productId") Long productId);

    int insertInventory(TccInventory inventory);

    int freezeInventory(@Param("productId") Long productId, @Param("quantity") int quantity);

    int confirmInventory(@Param("productId") Long productId, @Param("quantity") int quantity);

    int cancelInventory(@Param("productId") Long productId, @Param("quantity") int quantity);

    TccAccount selectAccount(@Param("userId") Long userId);

    int insertAccount(TccAccount account);

    int freezeAccount(@Param("userId") Long userId, @Param("amount") long amount);

    int confirmAccount(@Param("userId") Long userId, @Param("amount") long amount);

    int cancelAccount(@Param("userId") Long userId, @Param("amount") long amount);

    TccOrder selectOrderByXid(@Param("xid") String xid);

    int insertOrder(TccOrder order);

    int updateOrderStatus(@Param("xid") String xid, @Param("status") int status);

}
