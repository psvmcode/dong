package com.dong.redpacket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 红包查询请求。详情、领取记录、剩余金额三个接口共用，
 * 因为它们的入参都只有一个红包编号。
 */
public class RedPacketQueryRequest {

    /**
     * 红包编号。
     */
    @NotBlank
    @Size(max = 128)
    private String packetNo;

    /**
     * 获取红包编号。
     *
     * @return 红包编号
     */
    public String getPacketNo() {
        return packetNo;
    }

    /**
     * 设置红包编号。
     *
     * @param packetNo 红包编号
     */
    public void setPacketNo(String packetNo) {
        this.packetNo = packetNo;
    }

}
