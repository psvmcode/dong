package com.dong.ad.mapper;

import com.dong.ad.entity.AdDailyStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 投放日汇总数据访问接口。
 */
@Mapper
public interface AdDailyStatMapper {

    /**
     * 查询某计划某天的汇总行。
     *
     * @param statDate   统计日期
     * @param campaignId 计划 id
     * @return 汇总行
     */
    AdDailyStat selectByDateAndCampaign(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId);

    /**
     * 分页查询日报表。
     *
     * @param dateFrom   起始日期
     * @param dateTo     结束日期
     * @param campaignId 计划过滤
     * @param offset     偏移量
     * @param size       每页大小
     * @return 汇总列表
     */
    List<AdDailyStat> selectByPage(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo, @Param("campaignId") Long campaignId, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计分页查询总数。
     *
     * @param dateFrom   起始日期
     * @param dateTo     结束日期
     * @param campaignId 计划过滤
     * @return 总数
     */
    long countByPage(@Param("dateFrom") LocalDate dateFrom, @Param("dateTo") LocalDate dateTo, @Param("campaignId") Long campaignId);

    /**
     * 初始化当天汇总行。重复初始化不覆盖已有数据，避免并发首笔计费互相重置计数。
     *
     * @param statDate    统计日期
     * @param campaignId  计划 id
     * @param budgetCents 日预算快照，单位分
     * @return 影响行数
     */
    int insertIfAbsent(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId, @Param("budgetCents") long budgetCents);

    /**
     * 条件累加已计费流水。日预算充足条件写在 where 里，靠影响行数判定是否超投。
     *
     * @param statDate    统计日期
     * @param campaignId  计划 id
     * @param amountCents 本笔金额，单位分
     * @param impressions 曝光增量
     * @param clicks      点击增量
     * @return 影响行数
     */
    int accumulateCharged(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId, @Param("amountCents") long amountCents, @Param("impressions") long impressions, @Param("clicks") long clicks);

    /**
     * 只累加效果数据，用于 CPC 计划的曝光、CPM 计划的点击这类不扣钱的事件。
     *
     * @param statDate    统计日期
     * @param campaignId  计划 id
     * @param impressions 曝光增量
     * @param clicks      点击增量
     * @return 影响行数
     */
    int accumulateEffect(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId, @Param("impressions") long impressions, @Param("clicks") long clicks);

    /**
     * 累加被护栏拒绝的事件。效果数据照常统计，金额记到 rejected 侧。
     *
     * @param statDate    统计日期
     * @param campaignId  计划 id
     * @param amountCents 被挡下的金额，单位分
     * @param impressions 曝光增量
     * @param clicks      点击增量
     * @return 影响行数
     */
    int accumulateRejected(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId, @Param("amountCents") long amountCents, @Param("impressions") long impressions, @Param("clicks") long clicks);

    /**
     * 刷新当天预算快照，调整日预算后必须调用，否则条件扣减还按旧预算挡着。
     *
     * @param statDate    统计日期
     * @param campaignId  计划 id
     * @param budgetCents 新的日预算，单位分
     * @return 影响行数
     */
    int updateBudgetSnapshot(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId, @Param("budgetCents") long budgetCents);

    /**
     * 对账后以流水为权威源覆盖汇总行，重跑同一天结果必须一致。
     * 刻意不动 budget_cents：它是当天的预算快照，历史行不能被今天的预算改写。
     *
     * @param stat 汇总实体，含重算后的全部计数
     * @return 影响行数
     */
    int overwrite(@Param("stat") AdDailyStat stat);

}
