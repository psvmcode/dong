package com.dong.lab.redpacket.mapper;

import com.dong.lab.redpacket.entity.RedPacket;
import com.dong.lab.redpacket.entity.RedPacketRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RedPacketMapper {

    RedPacket selectByPacketNo(@Param("packetNo") String packetNo);

    int insert(RedPacket redPacket);

    int updateStatus(@Param("packetNo") String packetNo, @Param("status") int status);

    int decreaseRemain(@Param("packetNo") String packetNo,
                       @Param("amount") long amount,
                       @Param("count") int count);

    List<RedPacketRecord> selectRecords(@Param("packetNo") String packetNo);

    int insertRecord(RedPacketRecord record);

    int countRecord(@Param("packetNo") String packetNo, @Param("userId") Long userId);

}
