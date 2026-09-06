package com.dong.redpacket.service;

import com.dong.redpacket.dto.GrabResultResponse;
import com.dong.redpacket.dto.RedPacketSendRequest;
import com.dong.redpacket.entity.RedPacket;
import com.dong.redpacket.entity.RedPacketRecord;

import java.util.List;

/**
 * 抢红包。发红包时就把金额算好并推入 Redis List，
 * 抢的时候只是一次原子弹出，全程无锁无事务，再多人并发也不会竞争。
 */
public interface RedPacketService {

    /**
     * 发红包，金额预先分配好后写入 Redis，返回红包编号。
     */
    String send(RedPacketSendRequest request);

    /**
     * 抢红包，从预分配列表中原子弹出一份。已抢完或重复抢会被拒绝。
     */
    GrabResultResponse grab(String packetNo, Long userId);

    /**
     * 按编号查询红包。
     */
    RedPacket findByPacketNo(String packetNo);

    /**
     * 查询领取记录，可用它核对金额是否精确守恒。
     */
    List<RedPacketRecord> records(String packetNo);

    /**
     * 查询剩余份数。
     */
    int remainCount(String packetNo);

    /**
     * 查询剩余金额。
     */
    long remainAmount(String packetNo);

}
