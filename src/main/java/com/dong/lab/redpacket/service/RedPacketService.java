package com.dong.lab.redpacket.service;

import com.dong.lab.redpacket.dto.GrabResultResponse;
import com.dong.lab.redpacket.dto.RedPacketSendRequest;
import com.dong.lab.redpacket.entity.RedPacket;
import com.dong.lab.redpacket.entity.RedPacketRecord;

import java.util.List;

public interface RedPacketService {

    String send(RedPacketSendRequest request);

    GrabResultResponse grab(String packetNo, Long userId);

    RedPacket findByPacketNo(String packetNo);

    List<RedPacketRecord> records(String packetNo);

    int remainCount(String packetNo);

    long remainAmount(String packetNo);

}
