package com.dong.lab.seckill.service;

public interface SeckillStockService {

    void prepare(Long activityId, int totalStock);

    int deduct(Long activityId, Long userId, int quantity);

    int rollback(Long activityId, Long userId, int quantity);

    int available(Long activityId);

    void clear(Long activityId);

}
