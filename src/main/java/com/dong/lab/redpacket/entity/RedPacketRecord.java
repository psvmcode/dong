package com.dong.lab.redpacket.entity;

import lombok.Data;

import java.time.LocalDateTime;

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
