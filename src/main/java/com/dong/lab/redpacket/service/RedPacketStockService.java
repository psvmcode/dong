package com.dong.lab.redpacket.service;

import java.util.List;

public interface RedPacketStockService {

    void prepare(String packetNo, List<Long> amounts, long totalAmount);

    long grab(String packetNo, Long userId);

    int remainCount(String packetNo);

    long remainAmount(String packetNo);

}
