package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 分布式锁对照实验结果。
 *
 * <p>这个实验的核心是「丢失更新数」：期望值与实际值的差。
 * 不加锁时差得很多，加锁时为零，代价是耗时高出一到两个数量级。
 *
 * <p>落库后可以回看历史实验结果，不必每次重新跑一遍。
 * 锁等待超时的次数必须单独记录——混进丢失更新会让实验结论失真。
 */
@Data

public class ClassicLockLabResult {

    /**
     * 主键
     */
    private Long id;

    /**
     * 实验模式：no-lock 不加锁 redisson-lock 加锁
     */
    private String mode;

    /**
     * 期望计数，等于线程数乘以每线程循环数
     */
    private Integer expectedCount;

    /**
     * 实际计数，不加锁时会小于期望值
     */
    private Integer actualCount;

    /**
     * 成功拿到锁的次数
     */
    private Integer lockAcquired;

    /**
     * 等待锁超时的次数
     */
    private Integer lockTimedOut;

    /**
     * 丢失的更新数，等于期望减实际，是本实验的核心指标
     */
    private Integer lostUpdates;

    /**
     * 实验耗时，单位毫秒
     */
    private Long elapsedMillis;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
