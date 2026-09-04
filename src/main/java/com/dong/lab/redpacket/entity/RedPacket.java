package com.dong.lab.redpacket.entity;

import com.dong.lab.redpacket.enums.RedPacketStatus;
import com.dong.lab.redpacket.enums.RedPacketType;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * 红包。记录红包的总金额、总份数与剩余情况，
 * 剩余金额为零或过期后状态流转到结束。
 */
@Data

public class RedPacket {

    /**
     * 主键
     */
    private Long id;

    /**
     * 红包编号，抢红包时使用
     */
    private String packetNo;

    /**
     * 发红包用户 id
     */
    private Long sponsorId;

    /**
     * 红包总金额，单位分
     */
    private Long totalAmount;

    /**
     * 红包总份数
     */
    private Integer totalCount;

    /**
     * 剩余金额，单位分，抢完为零
     */
    private Long remainAmount;

    /**
     * 剩余份数
     */
    private Integer remainCount;

    /**
     * 红包类型，1 拼手气 2 普通均分
     */
    private RedPacketType packetType;

    /**
     * 红包状态
     */
    private RedPacketStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
