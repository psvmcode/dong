package com.dong.classic.mapper;

import com.dong.classic.entity.ClassicLeaderboardSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 排行榜持久化数据访问。
 */
@Mapper

public interface ClassicLeaderboardSnapshotMapper {

    /**
     * 写入或更新成员分数。用 upsert 是因为分数是覆盖语义，
     * 不是累加语义，重复提交应保留最新值。
     */
    int upsert(@Param("board") String board, @Param("member") String member, @Param("score") double score);

    /**
     * 按榜单查前 N 名，作为数据库侧的兜底排名。
     */
    List<ClassicLeaderboardSnapshot> selectTop(@Param("board") String board, @Param("limit") int limit);

    /**
     * 统计榜单成员数，用于与 ZSet 的大小对账。
     */
    long countByBoard(@Param("board") String board);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
