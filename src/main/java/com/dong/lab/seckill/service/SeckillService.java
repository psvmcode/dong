package com.dong.lab.seckill.service;

import com.dong.lab.seckill.dto.SeckillActivityRequest;
import com.dong.lab.seckill.dto.SeckillReceiptResponse;

import java.util.List;

/**
 * 秒杀。核心思路是把库存决策从数据库搬到 Redis，
 * 用一条 Lua 脚本原子完成查余额、扣减、记录用户，全程无锁无事务，
 * 下单再交由消息队列异步处理。
 */
public interface SeckillService {

    /**
     * 创建秒杀活动。
     */
    Long createActivity(SeckillActivityRequest request);

    /**
     * 预热库存到 Redis。不预热的话库存全在数据库里，高并发下会被直接打穿。
     */
    int prepare(Long activityId);

    /**
     * 秒杀下单。先在 Redis 扣库存，扣成功才发消息异步建单，
     * 返回的是受理凭证而非订单，因为订单还没落库。
     */
    SeckillReceiptResponse seckill(Long activityId, Long userId, int quantity);

    /**
     * 查询 Redis 中的剩余库存。
     */
    int stockOf(Long activityId);

    /**
     * 查询全部活动。
     */
    List<com.dong.lab.seckill.entity.SeckillActivity> activities();

    /**
     * 按订单号查询订单。
     */
    com.dong.lab.seckill.entity.SeckillOrder order(String orderNo);

    /**
     * 支付。
     */
    void pay(String orderNo);

    /**
     * 取消订单并回滚库存。
     */
    void cancel(String orderNo);

    /**
     * 查看运行时状态，含售罄标记等。
     */
    java.util.Map<String, Object> runtime();

}
