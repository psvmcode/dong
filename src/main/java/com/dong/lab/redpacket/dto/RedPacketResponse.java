package com.dong.lab.redpacket.dto;

import com.dong.lab.redpacket.entity.RedPacket;

import java.time.LocalDateTime;

public class RedPacketResponse {

    private String packetNo;

    private Long sponsorId;

    private Long totalAmount;

    private Integer totalCount;

    private Long remainAmount;

    private Integer remainCount;

    private String packetType;

    private String status;

    private LocalDateTime createTime;

    public static RedPacketResponse from(RedPacket redPacket) {
        RedPacketResponse response = new RedPacketResponse();
        response.setPacketNo(redPacket.getPacketNo());
        response.setSponsorId(redPacket.getSponsorId());
        response.setTotalAmount(redPacket.getTotalAmount());
        response.setTotalCount(redPacket.getTotalCount());
        response.setRemainAmount(redPacket.getRemainAmount());
        response.setRemainCount(redPacket.getRemainCount());
        response.setPacketType(redPacket.getPacketType() == null ? null : redPacket.getPacketType().name());
        response.setStatus(redPacket.getStatus() == null ? null : redPacket.getStatus().name());
        response.setCreateTime(redPacket.getCreateTime());
        return response;
    }

    public String getPacketNo() {
        return packetNo;
    }

    public void setPacketNo(String packetNo) {
        this.packetNo = packetNo;
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

    public Long getRemainAmount() {
        return remainAmount;
    }

    public void setRemainAmount(Long remainAmount) {
        this.remainAmount = remainAmount;
    }

    public Integer getRemainCount() {
        return remainCount;
    }

    public void setRemainCount(Integer remainCount) {
        this.remainCount = remainCount;
    }

    public String getPacketType() {
        return packetType;
    }

    public void setPacketType(String packetType) {
        this.packetType = packetType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
