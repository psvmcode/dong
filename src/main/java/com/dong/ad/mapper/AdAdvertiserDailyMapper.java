package com.dong.ad.mapper;

import com.dong.ad.entity.AdAdvertiserDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 广告主账户日汇总数据访问接口。
 */
@Mapper
public interface AdAdvertiserDailyMapper {

    /**
     * 查询某广告主某天的账户汇总。
     *
     * @param statDate     统计日期
     * @param advertiserId 广告主 id
     * @return 账户汇总
     */
    AdAdvertiserDaily selectByDateAndAdvertiser(@Param("statDate") LocalDate statDate, @Param("advertiserId") Long advertiserId);

    /**
     * 初始化当天账户汇总行，重复初始化不覆盖已有金额。
     *
     * @param statDate         统计日期
     * @param advertiserId     广告主 id
     * @param dailyLimitCents  账户日限额快照，单位分
     * @return 影响行数
     */
    int insertIfAbsent(@Param("statDate") LocalDate statDate, @Param("advertiserId") Long advertiserId, @Param("dailyLimitCents") long dailyLimitCents);

    /**
     * 条件累加账户当日花费。日限额为 0 表示不限，非零时把充足条件写进 where。
     *
     * @param statDate     统计日期
     * @param advertiserId 广告主 id
     * @param amountCents  本笔金额，单位分
     * @return 影响行数
     */
    int accumulateCharged(@Param("statDate") LocalDate statDate, @Param("advertiserId") Long advertiserId, @Param("amountCents") long amountCents);

    /**
     * 反向补偿账户当日花费。
     *
     * @param statDate     统计日期
     * @param advertiserId 广告主 id
     * @param amountCents  回退金额，单位分
     * @return 影响行数
     */
    int subtractCharged(@Param("statDate") LocalDate statDate, @Param("advertiserId") Long advertiserId, @Param("amountCents") long amountCents);

    /**
     * 调整账户日限额时刷新当天快照。
     *
     * @param statDate         统计日期
     * @param advertiserId     广告主 id
     * @param dailyLimitCents  新的日限额，单位分
     * @return 影响行数
     */
    int updateLimitSnapshot(@Param("statDate") LocalDate statDate, @Param("advertiserId") Long advertiserId, @Param("dailyLimitCents") long dailyLimitCents);

    /**
     * 对账后以流水为权威源覆盖账户当日花费，不动限额快照。
     *
     * @param statDate     统计日期
     * @param advertiserId 广告主 id
     * @param chargeCents  重算后的金额，单位分
     * @return 影响行数
     */
    int overwrite(@Param("statDate") LocalDate statDate, @Param("advertiserId") Long advertiserId, @Param("chargeCents") long chargeCents);

}
