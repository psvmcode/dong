package com.dong.redpacket.service;

import java.util.List;

/**
 * 红包库存。发红包时把预分配好的金额列表整体推入 Redis List，
 * 抢的时候只是一次 RPOP，原子性由 Redis 单线程保证，不需要额外加锁。
 */
public interface RedPacketStockService {

    /**
     * 预分配入队。
     */
    void prepare(String packetNo, List<Long> amounts, long totalAmount);

    /**
     * 抢一份，原子弹出。返回 0 表示已抢完或该用户已抢过。
     */
    long grab(String packetNo, Long userId);

    /**
     * 剩余份数。
     */
    int remainCount(String packetNo);

    /**
     * 剩余金额。
     */
    long remainAmount(String packetNo);

}
