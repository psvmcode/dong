package com.dong.redpacket.entity;

import com.dong.redpacket.enums.RedPacketItemStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 红包预分配份额。发红包时就把每一份的金额算好并按序号落库，
 * 因此即使 Redis 里的待发队列全部丢失，也能原样重建，金额仍然精确守恒。
 */
@Data
public class RedPacketItem {

    /**
     * 主键
     */
    private Long id;

    /**
     * 红包编号
     */
    private String packetNo;

    /**
     * 分配序号，Redis 队列里存的就是它
     */
    private Integer seq;

    /**
     * 预分配金额，单位分
     */
    private Long amount;

    /**
     * 领取状态
     */
    private RedPacketItemStatus status;

    /**
     * 领取用户 id，未领取时为空
     */
    private Long userId;

    /**
     * 领取时间
     */
    private LocalDateTime grabTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 构造一个待发送的份额。
     */
    public static RedPacketItem pending(String packetNo, int seq, long amount) {
        RedPacketItem item = new RedPacketItem();
        item.setPacketNo(packetNo);
        item.setSeq(seq);
        item.setAmount(amount);
        item.setStatus(RedPacketItemStatus.UNCLAIMED);
        return item;
    }

    /**
     * 转成 Redis 队列里的元素文本，格式为 序号:金额。
     */
    public String toQueueValue() {
        return seq + ":" + amount;
    }

}
