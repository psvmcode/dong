package com.dong.redpacket.dto;

import com.dong.redpacket.entity.RedPacket;

import java.time.LocalDateTime;

/**
 * 红包详情响应。
 */
public class RedPacketResponse {

    /**
     * 红包编号。
     */
    private String packetNo;

    /**
     * 发红包用户 id。
     */
    private Long sponsorId;

    /**
     * 红包总金额，单位分。
     */
    private Long totalAmount;

    /**
     * 红包总份数。
     */
    private Integer totalCount;

    /**
     * 剩余金额，单位分。
     */
    private Long remainAmount;

    /**
     * 剩余份数。
     */
    private Integer remainCount;

    /**
     * 红包类型。
     */
    private String packetType;

    /**
     * 状态。
     */
    private String status;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 从实体转换为 DTO。
     */
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

    /**
     * 获取红包编号。
     */
    public String getPacketNo() {
        return packetNo;
    }

    /**
     * 设置红包编号。
     */
    public void setPacketNo(String packetNo) {
        this.packetNo = packetNo;
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
     * 获取剩余金额。
     */
    public Long getRemainAmount() {
        return remainAmount;
    }

    /**
     * 设置剩余金额。
     */
    public void setRemainAmount(Long remainAmount) {
        this.remainAmount = remainAmount;
    }

    /**
     * 获取剩余份数。
     */
    public Integer getRemainCount() {
        return remainCount;
    }

    /**
     * 设置剩余份数。
     */
    public void setRemainCount(Integer remainCount) {
        this.remainCount = remainCount;
    }

    /**
     * 获取红包类型。
     */
    public String getPacketType() {
        return packetType;
    }

    /**
     * 设置红包类型。
     */
    public void setPacketType(String packetType) {
        this.packetType = packetType;
    }

    /**
     * 获取状态。
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态。
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取创建时间。
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间。
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

}
