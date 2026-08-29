package com.dong.lab.redpacket.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedPacketRecord {

    private Long id;

    private String packetNo;

    private Long userId;

    private Long amount;

    private LocalDateTime createTime;

}
