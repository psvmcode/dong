package com.dong.redpacket.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 红包领取记录。记录某个用户抢到某个红包的金额与时间，
 * 与红包表配合用于展示领取明细和核对总金额。
 */
@Data

public class RedPacketRecord {

    /**
     * 主键
     */
    private Long id;

    /**
     * 红包编号
     */
    private String packetNo;

    /**
     * 抢到红包的用户 id
     */
    private Long userId;

    /**
     * 抢到金额，单位分
     */
    private Long amount;

    /**
     * 抢到时间
     */
    private LocalDateTime createTime;

}
