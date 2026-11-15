package com.dong.ad.service;

import java.time.LocalDate;

/**
 * 预算闸门与频次控制。
 *
 * <p>Redis 里的剩余配额只是数据库流水的副本：首次访问用库里的今日已花费惰性初始化，
 * 挂掉后由对账任务重建，因此不需要人工预热，也不会因为副本丢失而多扣钱。
 */
public interface AdBudgetService {

    /**
     * 原子预扣当日预算配额。
     *
     * @param campaignId        计划 id
     * @param date              归属日期
     * @param dailyBudgetCents  日预算，单位分
     * @param spentTodayCents   库中今日已计费金额，单位分
     * @param amountCents       本笔金额，单位分
     * @return 扣减后剩余配额，-2 表示配额不足；Redis 不可用直接抛异常由调用方降级
     */
    long reserve(Long campaignId, LocalDate date, long dailyBudgetCents, long spentTodayCents, long amountCents);

    /**
     * 归还预扣配额。仅在键仍存在时回加，避免给早已过期的日期造出一个幽灵配额。
     *
     * @param campaignId  计划 id
     * @param date        归属日期
     * @param amountCents 归还金额，单位分
     */
    void restore(Long campaignId, LocalDate date, long amountCents);

    /**
     * 以数据库流水为准重建当日配额副本。
     *
     * @param campaignId       计划 id
     * @param date             归属日期
     * @param dailyBudgetCents 日预算，单位分
     * @param spentTodayCents  库中今日已计费金额，单位分
     */
    void rebuild(Long campaignId, LocalDate date, long dailyBudgetCents, long spentTodayCents);

    /**
     * 读取用户当日在该计划下的曝光次数，Redis 不可用抛异常由调用方降级为不限频。
     *
     * @param campaignId 计划 id
     * @param userId     用户 id
     * @param date       归属日期
     * @return 已曝光次数
     */
    long frequencyCount(Long campaignId, Long userId, LocalDate date);

    /**
     * 有效曝光后递增频次计数，键首次出现时设置到日终的 TTL。
     *
     * @param campaignId 计划 id
     * @param userId     用户 id
     * @param date       归属日期
     */
    void incrementFrequency(Long campaignId, Long userId, LocalDate date);

}
