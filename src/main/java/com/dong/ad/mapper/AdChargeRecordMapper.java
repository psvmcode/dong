package com.dong.ad.mapper;

import com.dong.ad.entity.AdChargeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 计费流水数据访问接口。流水是金额的唯一权威源，对账从这里重算。
 */
@Mapper
public interface AdChargeRecordMapper {

    /**
     * 新增流水。uk_charge_event 让消息重投天然幂等。
     *
     * @param record 流水实体
     * @return 影响行数
     */
    int insert(AdChargeRecord record);

    /**
     * 按幂等键统计流水数。
     *
     * @param eventId 事件幂等键
     * @return 流水数
     */
    long countByEventId(@Param("eventId") String eventId);

    /**
     * 按归属日期统计流水数。超限必须对账报错，截断会得出错误的金额结论。
     *
     * @param statDate 归属日期
     * @return 流水数
     */
    long countByStatDate(@Param("statDate") LocalDate statDate);

    /**
     * 查询某归属日的全部流水，SQL 层按上限截断。
     *
     * @param statDate 归属日期
     * @param limit    最大条数
     * @return 流水列表
     */
    List<AdChargeRecord> selectByStatDate(@Param("statDate") LocalDate statDate, @Param("limit") int limit);

    /**
     * 统计计划累计已计费金额，单位分。
     *
     * @param campaignId 计划 id
     * @return 累计金额
     */
    long sumByCampaign(@Param("campaignId") Long campaignId);

    /**
     * 按广告主汇总某天的已入账金额。用 SQL 聚合而不是把流水读进内存，
     * 这样大促当天也能对账户日限额做权威重算。
     *
     * @param statDate     归属日期
     * @param advertiserId 广告主 id
     * @return 已入账金额，单位分
     */
    long sumByAdvertiserAndDate(@Param("statDate") LocalDate statDate, @Param("advertiserId") Long advertiserId);

    /**
     * 分页查询某天有流水的计划 id，对账分片用。
     *
     * @param statDate 归属日期
     * @param offset   偏移量
     * @param size     每片大小
     * @return 计划 id 列表
     */
    List<Long> selectCampaignIdsByStatDate(@Param("statDate") LocalDate statDate, @Param("offset") int offset, @Param("size") int size);

    /**
     * 查询某计划某天的流水，单片按上限截断。
     *
     * @param statDate   归属日期
     * @param campaignId 计划 id
     * @param limit      最大条数
     * @return 流水列表
     */
    List<AdChargeRecord> selectByStatDateAndCampaign(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId, @Param("limit") int limit);

    /**
     * 统计某计划某天的流水数。超限必须报错而不是截断。
     *
     * @param statDate   归属日期
     * @param campaignId 计划 id
     * @return 流水数
     */
    long countByStatDateAndCampaign(@Param("statDate") LocalDate statDate, @Param("campaignId") Long campaignId);

    /**
     * 查询创建时间早于指定时点的流水，归档搬运用。
     *
     * @param before 时间上界
     * @param limit  最大条数
     * @return 流水列表
     */
    List<AdChargeRecord> selectExpired(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 按 id 批量删除已归档的在线流水。
     *
     * @param ids 流水 id 列表
     * @return 影响行数
     */
    int deleteByIds(@Param("ids") List<Long> ids);

}
