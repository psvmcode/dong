package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 排行榜持久化。
 *
 * <p>ZSet 的写入与查排名都是对数复杂度，高频更新场景下远快于数据库排序分页。
 * 但它同样存在 Redis，一旦数据丢失，榜单就空了。
 *
 * <p>本表保存每个成员的最新分数，既是 Redis 的兜底，
 * 也能在不依赖 Redis 的情况下做离线统计与核对。
 */
@Data

public class ClassicLeaderboardSnapshot {

    /**
     * 主键
     */
    private Long id;

    /**
     * 榜单标识
     */
    private String board;

    /**
     * 成员标识
     */
    private String member;

    /**
     * 当前分数，与 Redis ZSet 保持一致
     */
    private Double score;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
