package com.dong.lab.seckill.mapper;

import com.dong.lab.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillOrderMapper {

    SeckillOrder selectByOrderNo(@Param("orderNo") String orderNo);

    int countByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);

    int insert(SeckillOrder order);

    int updateStatus(@Param("orderNo") String orderNo, @Param("status") int status);

    List<SeckillOrder> selectTimeoutCandidates(@Param("status") int status, @Param("minutes") int minutes);

}
