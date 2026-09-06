package com.dong.order.mapper;

import com.dong.order.entity.TradeOrder;
import com.dong.order.enums.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 订单 Mapper。状态推进一律带期望状态与版本号，
 * 返回值是受影响行数，0 就说明有人抢先改过了。
 */
@Mapper

public interface TradeOrderMapper {

    /**
     * 插入订单。
     *
     * @param order 订单实体
     * @return 受影响行数
     */
    int insert(TradeOrder order);

    /**
     * 按订单号查询订单。
     *
     * @param orderNo 订单号
     * @return 订单实体
     */
    TradeOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 乐观锁推进状态，期望状态与版本号同时匹配才更新。
     * 业务字段全都走动态 SQL，只有非空的才写，避免用旧值覆盖新值。
     *
     * @param order 待写入的订单，status 为目标状态，version 为读到的旧版本号
     * @param expected 期望的当前状态
     * @return 受影响行数，0 表示被并发抢先
     */
    int updateStatus(@Param("order") TradeOrder order, @Param("expected") OrderStatus expected);

    /**
     * 无保护推进状态，并发对比实验专用。不带期望状态也不带版本号，
     * 后到的更新会直接覆盖先到的，用来量化「不做防护会发生什么」。
     *
     * @param orderNo 订单号
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatusUnlocked(@Param("orderNo") String orderNo, @Param("status") OrderStatus status);

    /**
     * 累加催单次数。内部迁移不改状态，不需要乐观锁。
     *
     * @param orderNo 订单号
     * @return 受影响行数
     */
    int increaseUrgeCount(@Param("orderNo") String orderNo);

    /**
     * 查询最近创建的订单。
     *
     * @param limit 条数
     * @return 订单列表
     */
    List<TradeOrder> selectRecent(@Param("limit") int limit);

    /**
     * 按订单号删除订单，实验环境清理用。
     *
     * @param orderNo 订单号
     * @return 受影响行数
     */
    int deleteByOrderNo(@Param("orderNo") String orderNo);

}
