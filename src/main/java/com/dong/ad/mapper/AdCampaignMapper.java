package com.dong.ad.mapper;

import com.dong.ad.entity.AdCampaign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 推广计划数据访问接口。
 */
@Mapper
public interface AdCampaignMapper {

    /**
     * 根据 id 查询计划。
     *
     * @param id 主键
     * @return 计划
     */
    AdCampaign selectById(@Param("id") Long id);

    /**
     * 根据编号查询计划。
     *
     * @param campaignNo 计划编号
     * @return 计划
     */
    AdCampaign selectByNo(@Param("campaignNo") String campaignNo);

    /**
     * 分页查询计划。
     *
     * @param status       状态过滤
     * @param advertiserId 广告主过滤
     * @param keyword      名称或编号关键字
     * @param offset       偏移量
     * @param size         每页大小
     * @return 计划列表
     */
    List<AdCampaign> selectByPage(@Param("status") Integer status, @Param("advertiserId") Long advertiserId, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计分页查询总数。
     *
     * @param status       状态过滤
     * @param advertiserId 广告主过滤
     * @param keyword      名称或编号关键字
     * @return 总数
     */
    long countByPage(@Param("status") Integer status, @Param("advertiserId") Long advertiserId, @Param("keyword") String keyword);

    /**
     * 查询可参与召回的计划，SQL 层按上限截断。
     *
     * @param status 状态
     * @param date   投放日期
     * @param limit  最大条数
     * @return 计划列表
     */
    List<AdCampaign> selectDeliverable(@Param("status") int status, @Param("date") LocalDate date, @Param("limit") int limit);

    /**
     * 查询全部计划，供对账使用，SQL 层按上限截断。
     *
     * @param limit 最大条数
     * @return 计划列表
     */
    List<AdCampaign> selectAll(@Param("limit") int limit);

    /**
     * 新增计划。
     *
     * @param campaign 计划实体
     * @return 影响行数
     */
    int insert(AdCampaign campaign);

    /**
     * 条件推进状态。期望状态与版本号都写进 where，
     * 两个管理员同时点上线时只有一个能成功。
     *
     * @param id              计划 id
     * @param status          目标状态
     * @param expectedStatus  期望当前状态
     * @param expectedVersion 期望版本号
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") int status, @Param("expectedStatus") int expectedStatus, @Param("expectedVersion") int expectedVersion);

    /**
     * 调整预算、结束日期与投放时段，非空字段才更新。
     *
     * @param campaign        计划实体，仅取需要更新的字段
     * @param expectedVersion 期望版本号
     * @return 影响行数
     */
    int updateConfig(@Param("campaign") AdCampaign campaign, @Param("expectedVersion") int expectedVersion);

    /**
     * 条件累加累计花费，总预算约束写进 where。
     *
     * @param id          计划 id
     * @param amountCents 本笔金额，单位分
     * @return 影响行数
     */
    int addSpent(@Param("id") Long id, @Param("amountCents") long amountCents);

    /**
     * 反向补偿累计花费，仅在并发下后续条件更新失败时调用。
     * where 带充足条件，避免把累计花费减成负数。
     *
     * @param id          计划 id
     * @param amountCents 回退金额，单位分
     * @return 影响行数
     */
    int subtractSpent(@Param("id") Long id, @Param("amountCents") long amountCents);

    /**
     * 对账后以流水为权威源覆盖累计花费。
     *
     * @param id              计划 id
     * @param spentTotalCents 累计已花费，单位分
     * @return 影响行数
     */
    int overwriteSpent(@Param("id") Long id, @Param("spentTotalCents") long spentTotalCents);

}
