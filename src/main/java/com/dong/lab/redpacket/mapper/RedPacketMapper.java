package com.dong.lab.redpacket.mapper;

import com.dong.lab.redpacket.entity.RedPacket;
import com.dong.lab.redpacket.entity.RedPacketRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * RedPacketMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface RedPacketMapper {

    /**
     * 按 PacketNo 查询记录。
     */
    RedPacket selectByPacketNo(@Param("packetNo") String packetNo);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(RedPacket redPacket);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("packetNo") String packetNo, @Param("status") int status);

    /**
     * 扣减红包剩余金额与剩余个数。
     */
    int decreaseRemain(@Param("packetNo") String packetNo,
                       @Param("amount") long amount,
                       @Param("count") int count);

    /**
     * 查询红包领取记录。
     */
    List<RedPacketRecord> selectRecords(@Param("packetNo") String packetNo);

    /**
     * 插入领取记录，返回影响行数。
     */
    int insertRecord(RedPacketRecord record);

    /**
     * 统计红包领取记录数。
     */
    int countRecord(@Param("packetNo") String packetNo, @Param("userId") Long userId);

}
