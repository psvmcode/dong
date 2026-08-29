package com.dong.lab.redpacket.entity;

import com.dong.lab.redpacket.enums.RedPacketStatus;
import com.dong.lab.redpacket.enums.RedPacketType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedPacket {

    private Long id;

    private String packetNo;

    private Long sponsorId;

    private Long totalAmount;

    private Integer totalCount;

    private Long remainAmount;

    private Integer remainCount;

    private RedPacketType packetType;

    private RedPacketStatus status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
