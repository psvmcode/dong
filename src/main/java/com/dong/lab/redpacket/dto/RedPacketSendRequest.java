package com.dong.lab.redpacket.dto;

import com.dong.lab.redpacket.entity.RedPacket;
import com.dong.lab.redpacket.enums.RedPacketStatus;
import com.dong.lab.redpacket.enums.RedPacketType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RedPacketSendRequest {

    @NotNull
    private Long sponsorId;

    @NotNull
    @Min(1)
    private Long totalAmount;

    @NotNull
    @Min(1)
    private Integer totalCount;

    private Integer packetType;

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

    public Long getSponsorId() {
        return sponsorId;
    }

    public void setSponsorId(Long sponsorId) {
        this.sponsorId = sponsorId;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Long totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getPacketType() {
        return packetType;
    }

    public void setPacketType(Integer packetType) {
        this.packetType = packetType;
    }

}
