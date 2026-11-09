package com.dong.seckill.mapper;

import com.dong.seckill.entity.SeckillOrder;
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
     * 条件更新状态：只有当前状态等于 fromStatus 才更新，返回影响行数。
     * 无条件更新会把「查询到更新之间已被并发支付」的订单改成已取消，造成付款成功却钱货两空。
     */
    int updateStatus(@Param("orderNo") String orderNo, @Param("status") int status, @Param("fromStatus") int fromStatus);

    /**
     * 查询超时候选记录。
     */
    List<SeckillOrder> selectTimeoutCandidates(@Param("status") int status, @Param("minutes") int minutes);

}
