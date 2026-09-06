package com.dong.seckill.service;

/**
 * 秒杀库存。查余额、扣减、记录用户三步由一条 Lua 脚本原子完成，
 * 因此不需要分布式锁，也不存在读改写竞态。
 */
public interface SeckillStockService {

    /**
     * 预热库存。
     */
    void prepare(Long activityId, int totalStock);

    /**
     * 扣减库存。返回值语义容易踩坑：
     * 负数代表各种失败状态，非负数代表扣减后的剩余库存，
     * 不能用是否等于零来判断成败。
     */
    int deduct(Long activityId, Long userId, int quantity);

    /**
     * 回滚库存，取消订单或支付超时时调用。
     */
    int rollback(Long activityId, Long userId, int quantity);

    /**
     * 查询剩余库存。
     */
    int available(Long activityId);

    /**
     * 清空库存记录。
     */
    void clear(Long activityId);

}
