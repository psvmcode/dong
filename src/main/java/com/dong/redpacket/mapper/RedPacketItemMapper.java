package com.dong.redpacket.mapper;

import com.dong.redpacket.entity.RedPacketItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * RedPacketItemMapper，MyBatis 数据访问接口。
 */
@Mapper
public interface RedPacketItemMapper {

    /**
     * 批量写入预分配份额，返回影响行数。
     */
    int batchInsert(@Param("items") List<RedPacketItem> items);

    /**
     * 查询某红包尚未领取的份额，按序号升序。
     */
    List<RedPacketItem> selectUnclaimed(@Param("packetNo") String packetNo);

    /**
     * 查询一份可抢的份额，从指定序号开始向后找第一个未领取的。
     */
    RedPacketItem selectClaimCandidate(@Param("packetNo") String packetNo, @Param("seqFrom") int seqFrom);

    /**
     * 占位领取某一份额，带 status 条件做乐观锁。
     */
    int claim(@Param("packetNo") String packetNo, @Param("seq") int seq, @Param("userId") Long userId);

    /**
     * 统计某红包未领取的份数。
     */
    int countUnclaimed(@Param("packetNo") String packetNo);

    /**
     * 统计某红包未领取的金额总和。
     */
    Long sumUnclaimed(@Param("packetNo") String packetNo);

    /**
     * 退回已占位的份额，落库失败时用它把份额还给待发队列。
     */
    int release(@Param("packetNo") String packetNo, @Param("seq") int seq, @Param("userId") Long userId);

    /**
     * 统计某用户在该红包下已领取的份数，Redis 不可用时的去重依据。
     */
    int countClaimedByUser(@Param("packetNo") String packetNo, @Param("userId") Long userId);

}
