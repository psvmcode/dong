package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.ReconDiff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * ReconDiffMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface ReconDiffMapper {

    /**
     * 按 BatchNo 查询记录。
     */
    List<ReconDiff> selectByBatchNo(String batchNo);

    /**
     * 查询所有记录。
     */
    List<ReconDiff> selectAll(@Param("limit") int limit);

    /**
     * 查询未处理的差异记录。
     */
    List<ReconDiff> selectUnhandled(@Param("limit") int limit);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(ReconDiff diff);

    /**
     * 批量插入差异记录，对账一轮产生的多条差异一次性写入。
     */
    int batchInsert(@Param("items") List<ReconDiff> items);

    /**
     * 统计未处理的差异记录数。
     */
    long countUnhandled();

    /**
     * 按批次和差异类型统计记录数。
     */
    long countByBatchAndType(@Param("batchNo") String batchNo, @Param("diffType") int diffType);

    /**
     * 批量标记差异为已处理。
     */
    int markHandled(@Param("batchNo") String batchNo);

    /**
     * 单笔标记已处理，运营逐条处理差异时调用。
     */
    int markOneHandled(@Param("id") Long id);

    /**
     * 清空某批次的差异记录。对账重跑时先清旧结果再插入，
     * 保证重复对账不会产生成倍的重复差异。
     */
    int deleteByBatchNo(@Param("batchNo") String batchNo);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
