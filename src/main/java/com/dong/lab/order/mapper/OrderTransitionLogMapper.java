package com.dong.lab.order.mapper;

import com.dong.lab.order.entity.OrderTransitionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 订单状态流转日志 Mapper。
 */
@Mapper

public interface OrderTransitionLogMapper {

    /**
     * 写入一条流转记录，成功与失败都记。
     *
     * @param log 流转日志
     * @return 受影响行数
     */
    int insert(OrderTransitionLog log);

    /**
     * 查询订单的全部流转记录。
     *
     * @param orderNo 订单号
     * @return 流转记录列表
     */
    List<OrderTransitionLog> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 统计订单的流转记录条数，并发实验用它核对总次数。
     *
     * @param orderNo 订单号
     * @return 记录条数
     */
    int countByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 按订单号清空流转记录，实验环境清理用。
     *
     * @param orderNo 订单号
     * @return 受影响行数
     */
    int deleteByOrderNo(@Param("orderNo") String orderNo);

}
