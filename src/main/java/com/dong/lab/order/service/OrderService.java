package com.dong.lab.order.service;

import com.dong.lab.order.dto.OrderBenchmarkResponse;
import com.dong.lab.order.dto.OrderCreateRequest;
import com.dong.lab.order.dto.OrderFireRequest;
import com.dong.lab.order.dto.OrderResponse;
import com.dong.lab.order.dto.OrderTransitionLogResponse;

import java.util.List;
/**
 * 订单履约服务。所有状态变更都必须走 fire，业务代码不允许直接改状态字段。
 */
public interface OrderService {

    /**
     * 创建订单，初始停在待支付。
     *
     * @param request 创建请求
     * @return 订单号
     */
    String create(OrderCreateRequest request);

    /**
     * 触发事件推进订单状态。迁移不合法、守卫不通过、并发被抢先都会抛业务异常。
     *
     * @param orderNo 订单号
     * @param request 事件请求
     * @return 推进后的订单
     */
    OrderResponse fire(String orderNo, OrderFireRequest request);

    /**
     * 查询订单详情。
     *
     * @param orderNo 订单号
     * @return 订单
     */
    OrderResponse detail(String orderNo);

    /**
     * 查询订单的状态流转日志，被拒绝的记录也在里面。
     *
     * @param orderNo 订单号
     * @return 流转日志列表
     */
    List<OrderTransitionLogResponse> logs(String orderNo);

    /**
     * 查询订单当前状态下可以触发哪些事件。只判断有没有迁移定义，不判断守卫，
     * 所以列出来的事件仍有可能因为参数不全被拦下。
     *
     * @param orderNo 订单号
     * @return 事件名列表
     */
    List<String> availableEvents(String orderNo);

    /**
     * 查询最近创建的订单。
     *
     * @param limit 条数
     * @return 订单列表
     */
    List<OrderResponse> recent(int limit);

    /**
     * 并发对比实验。多个线程同时对同一张订单触发同一个事件，
     * 统计到底有几次真的推进成功。
     *
     * @param threads 并发线程数
     * @param mode 实验模式，cas 带乐观锁，none 不带
     * @return 实验结果
     */
    OrderBenchmarkResponse benchmark(int threads, String mode);

    /**
     * 导出状态机图，PlantUML 语法。
     *
     * @return PlantUML 文本
     */
    String plantUml();

    /**
     * 删除订单及其流转日志，实验环境清理用。
     *
     * @param orderNo 订单号
     */
    void remove(String orderNo);

}
