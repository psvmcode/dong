package com.dong.ad.mapper;

import com.dong.ad.entity.AdUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 广告单元数据访问接口。
 */
@Mapper
public interface AdUnitMapper {

    /**
     * 根据 id 查询单元。
     *
     * @param id 主键
     * @return 单元
     */
    AdUnit selectById(@Param("id") Long id);

    /**
     * 按计划 id 列表批量查启用单元。调用方保证列表非空，空列表不会走这条 SQL。
     *
     * @param campaignIds 计划 id 列表
     * @param status      状态
     * @param limit       最大条数
     * @return 单元列表
     */
    List<AdUnit> selectByCampaignIds(@Param("campaignIds") List<Long> campaignIds, @Param("status") int status, @Param("limit") int limit);

    /**
     * 分页查询单元。
     *
     * @param campaignId 计划过滤
     * @param status     状态过滤
     * @param keyword    名称或编号关键字
     * @param offset     偏移量
     * @param size       每页大小
     * @return 单元列表
     */
    List<AdUnit> selectByPage(@Param("campaignId") Long campaignId, @Param("status") Integer status, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计分页查询总数。
     *
     * @param campaignId 计划过滤
     * @param status     状态过滤
     * @param keyword    名称或编号关键字
     * @return 总数
     */
    long countByPage(@Param("campaignId") Long campaignId, @Param("status") Integer status, @Param("keyword") String keyword);

    /**
     * 统计计划下的启用单元数，计划上线守卫使用。
     *
     * @param campaignId 计划 id
     * @param status     状态
     * @return 单元数
     */
    long countByCampaign(@Param("campaignId") Long campaignId, @Param("status") int status);

    /**
     * 查询计划下全部单元，供实验与诊断使用。
     *
     * @param campaignId 计划 id
     * @param limit      最大条数
     * @return 单元列表
     */
    List<AdUnit> selectByCampaign(@Param("campaignId") Long campaignId, @Param("limit") int limit);

    /**
     * 新增单元。
     *
     * @param unit 单元实体
     * @return 影响行数
     */
    int insert(AdUnit unit);

    /**
     * 条件更新可配置项，带版本号防并发覆盖。
     *
     * @param unit            单元实体，仅取非空字段
     * @param expectedVersion 期望版本号
     * @return 影响行数
     */
    int updateConfig(@Param("unit") AdUnit unit, @Param("expectedVersion") int expectedVersion);

    /**
     * 条件更新启用状态。
     *
     * @param id              单元 id
     * @param status          目标状态
     * @param expectedVersion 期望版本号
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") int status, @Param("expectedVersion") int expectedVersion);

}
