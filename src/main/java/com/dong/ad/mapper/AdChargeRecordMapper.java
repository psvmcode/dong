package com.dong.ad.mapper;

import com.dong.ad.entity.AdChargeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
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

}
