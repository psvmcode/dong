package com.dong.lab.redpacket.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.redpacket.dto.GrabResultResponse;
import com.dong.lab.redpacket.dto.RedPacketSendRequest;
import com.dong.lab.redpacket.entity.RedPacket;
import com.dong.lab.redpacket.entity.RedPacketRecord;
import com.dong.lab.redpacket.enums.RedPacketStatus;
import com.dong.lab.redpacket.enums.RedPacketType;
import com.dong.lab.redpacket.mapper.RedPacketMapper;
import com.dong.lab.redpacket.support.RedPacketAllocator;
import com.dong.lab.redpacket.service.RedPacketService;
import com.dong.lab.redpacket.service.RedPacketStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
/**
 * 抢红包实现。发红包时就把金额算好并推入 Redis，
 * 抢的时候只是一次原子弹出，因此不需要锁，金额也能精确守恒。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class RedPacketServiceImpl implements RedPacketService {

    private static final String PACKET_NO_PREFIX = "RP";

    /**
     * redPacketMapper，MyBatis Mapper 数据访问层。
     */
    private final RedPacketMapper redPacketMapper;

    /**
     * redPacketStockService，业务服务层。
     */
    private final RedPacketStockService redPacketStockService;

    /**
     * snowflake，分布式唯一 ID 生成器。
     */
    private final Snowflake snowflake;

    /**
     * 发红包，预分配金额后写入 Redis 并返回红包编号。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String send(RedPacketSendRequest request) {
        String packetNo = PACKET_NO_PREFIX + snowflake.nextId();
        RedPacket redPacket = request.toEntity(packetNo);
        redPacketMapper.insert(redPacket);
        List<Long> amounts = redPacket.getPacketType() == RedPacketType.FIXED
                ? RedPacketAllocator.allocateFixed(request.getTotalAmount(), request.getTotalCount())
                : RedPacketAllocator.allocate(request.getTotalAmount(), request.getTotalCount());
        redPacketStockService.prepare(packetNo, amounts, request.getTotalAmount());
        log.info("red packet sent packetNo={} total={} count={}", packetNo,
                request.getTotalAmount(), request.getTotalCount());
        return packetNo;
    }

    /**
     * 抢红包，从预分配列表中原子弹出一份。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GrabResultResponse grab(String packetNo, Long userId) {
        RedPacket redPacket = findByPacketNo(packetNo);
        if (redPacket.getStatus() == RedPacketStatus.FINISHED) {
            return GrabResultResponse.failed("red packet is finished");
        }

        long amount = redPacketStockService.grab(packetNo, userId);
        if (amount < 0) {
            return GrabResultResponse.failed("nothing left or already grabbed");
        }

        RedPacketRecord record = new RedPacketRecord();
        record.setPacketNo(packetNo);
        record.setUserId(userId);
        record.setAmount(amount);
        redPacketMapper.insertRecord(record);
        redPacketMapper.decreaseRemain(packetNo, amount, 1);
        int remainCount = redPacketStockService.remainCount(packetNo);
        if (remainCount <= 0) {
            redPacketMapper.updateStatus(packetNo, RedPacketStatus.FINISHED.getCode());
        }

        log.info("red packet grabbed packetNo={} user={} amount={}", packetNo, userId, amount);
        return GrabResultResponse.success(amount);
    }

    /**
     * 按红包编号查询红包详情。
     */
    @Override
    public RedPacket findByPacketNo(String packetNo) {
        RedPacket redPacket = redPacketMapper.selectByPacketNo(packetNo);
        if (redPacket == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "red packet " + packetNo + " not found");
        }
        return redPacket;
    }

    /**
     * 查询红包领取记录。
     */
    @Override
    public List<RedPacketRecord> records(String packetNo) {
        return redPacketMapper.selectRecords(packetNo);
    }

    /**
     * 查询红包剩余份数。
     */
    @Override
    public int remainCount(String packetNo) {
        return redPacketStockService.remainCount(packetNo);
    }

    /**
     * 查询红包剩余金额。
     */
    @Override
    public long remainAmount(String packetNo) {
        return redPacketStockService.remainAmount(packetNo);
    }

}
