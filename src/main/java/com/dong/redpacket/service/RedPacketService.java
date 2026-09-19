package com.dong.redpacket.service;

import com.dong.redpacket.dto.GrabResultResponse;
import com.dong.redpacket.dto.RedPacketSendRequest;
import com.dong.redpacket.entity.RedPacket;
import com.dong.redpacket.entity.RedPacketRecord;

import java.util.List;
import java.util.Map;

/**
 * 抢红包。金额在发红包时按份算好并落库，Redis 队列只是这份数据的加速副本：
 * 抢的时候一次原子弹出，副本丢了能从库里原样重建，Redis 整体不可用还能降级到数据库。
 */
public interface RedPacketService {

    /**
     * 发红包，金额预先分配好后落库并预热 Redis，返回红包编号。
     */
    String send(RedPacketSendRequest request);

    /**
     * 抢红包，从待发队列原子弹出一份。已抢完或重复抢会被拒绝。
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
     * 查询剩余份数，Redis 不可用时回落到数据库。
     */
    int remainCount(String packetNo);

    /**
     * 查询剩余金额，Redis 不可用时回落到数据库。
     */
    long remainAmount(String packetNo);

    /**
     * 从数据库重建 Redis 库存，返回是否重建成功。
     */
    boolean rebuild(String packetNo);

    /**
     * 查看运行时状态，含限流、降级与重建次数。
     */
    Map<String, Object> runtime();

}
