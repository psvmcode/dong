package com.dong.lab.seckill.mapper;

import com.dong.lab.seckill.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * SeckillOrderMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface SeckillOrderMapper {

    /**
     * 按 OrderNo 查询记录。
     */
    SeckillOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 统计某用户在某活动下的订单数。
     */
    int countByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(SeckillOrder order);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") int status);

    /**
     * 查询超时候选记录。
     */
    List<SeckillOrder> selectTimeoutCandidates(@Param("status") int status, @Param("minutes") int minutes);

}
