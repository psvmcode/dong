package com.dong.redpacket.service;

import com.dong.redpacket.dto.GrabReservation;
import com.dong.redpacket.entity.RedPacketItem;

import java.util.List;
/**
 * 红包库存。Redis 里的待发队列只是数据库份额表的一份加速副本，
 * 丢了可以从库里原样重建，因此这里的每个方法都要说清"副本缺失"时是什么语义。
 */
public interface RedPacketStockService {

    /**
     * 预热库存，按份额序号整体入队。
     */
    void prepare(String packetNo, List<RedPacketItem> items, long totalAmount);

    /**
     * 从队列原子弹出一份。库存缺失返回 NOT_PREPARED，Redis 不可用返回 UNAVAILABLE，
     * 两种情况都不抛异常，交给调用方决定是重建还是降级。
     */
    GrabReservation reserve(String packetNo, Long userId);

    /**
     * 归还一份库存。落库失败时必须调用，否则这一份就凭空消失了。
     */
    void restore(String packetNo, Long userId, long amount, int seq);

    /**
     * 剩余份数，库存未预热返回 -1。
     */
    int remainCount(String packetNo);

    /**
     * 剩余金额，库存未预热返回 -1。
     */
    long remainAmount(String packetNo);

    /**
     * 库存是否已预热。
     */
    boolean prepared(String packetNo);

    /**
     * 用库里的未领取份额整体重建队列，并恢复已领取用户的去重集合。
     */
    void rebuild(String packetNo, List<RedPacketItem> pendingItems, List<Long> claimedUserIds);

}
