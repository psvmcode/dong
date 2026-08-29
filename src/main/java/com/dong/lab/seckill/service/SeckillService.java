package com.dong.lab.seckill.service;

import com.dong.lab.seckill.dto.SeckillActivityRequest;
import com.dong.lab.seckill.dto.SeckillReceiptResponse;

import java.util.List;

public interface SeckillService {

    Long createActivity(SeckillActivityRequest request);

    int prepare(Long activityId);

    SeckillReceiptResponse seckill(Long activityId, Long userId, int quantity);

    int stockOf(Long activityId);

    List<com.dong.lab.seckill.entity.SeckillActivity> activities();

    com.dong.lab.seckill.entity.SeckillOrder order(String orderNo);

    void pay(String orderNo);

    void cancel(String orderNo);

    java.util.Map<String, Object> runtime();

}
