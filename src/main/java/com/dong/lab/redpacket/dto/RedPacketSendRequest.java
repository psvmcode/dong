package com.dong.lab.redpacket.dto;

import com.dong.lab.redpacket.entity.RedPacket;
import com.dong.lab.redpacket.enums.RedPacketStatus;
import com.dong.lab.redpacket.enums.RedPacketType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 发红包请求参数。
 */
public class RedPacketSendRequest {

    @NotNull
    /**
     * 发红包用户 id。
     */
    private Long sponsorId;

    @NotNull
    @Min(1)
    /**
     * 红包总金额，单位分。
     */
    private Long totalAmount;

    @NotNull
    @Min(1)
    /**
     * 红包总份数。
     */
    private Integer totalCount;

    /**
     * 红包类型，1 拼手气 2 普通均分。
     */
    private Integer packetType;

    /**
     * 转换为红包实体。
     */
    public RedPacket toEntity(String packetNo) {
        RedPacket redPacket = new RedPacket();
        redPacket.setPacketNo(packetNo);
        redPacket.setSponsorId(sponsorId);
        redPacket.setTotalAmount(totalAmount);
        redPacket.setTotalCount(totalCount);
        redPacket.setRemainAmount(totalAmount);
        redPacket.setRemainCount(totalCount);
        redPacket.setPacketType(packetType == null
                ? RedPacketType.RANDOM
                : RedPacketType.of(packetType));
        redPacket.setStatus(RedPacketStatus.DISTRIBUTING);
        return redPacket;
    }

    /**
     * 获取发红包用户 id。
     */
    public Long getSponsorId() {
        return sponsorId;
    }

    /**
     * 设置发红包用户 id。
     */
    public void setSponsorId(Long sponsorId) {
        this.sponsorId = sponsorId;
    }

    /**
     * 获取红包总金额。
     */
    public Long getTotalAmount() {
        return totalAmount;
    }

    /**
     * 设置红包总金额。
     */
    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    /**
     * 获取红包总份数。
     */
    public Integer getTotalCount() {
        return totalCount;
    }

    /**
     * 设置红包总份数。
     */
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    /**
     * 获取红包类型。
     */
    public Integer getPacketType() {
        return packetType;
    }

    /**
     * 设置红包类型。
     */
    public void setPacketType(Integer packetType) {
        this.packetType = packetType;
    }

}
