package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.ReconDiff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconDiffMapper {

    List<ReconDiff> selectByBatchNo(String batchNo);

    List<ReconDiff> selectAll(@Param("limit") int limit);

    List<ReconDiff> selectUnhandled(@Param("limit") int limit);

    int insert(ReconDiff diff);

    /**
     * 批量插入差异记录，对账一轮产生的多条差异一次性写入。
     */
    int batchInsert(@Param("items") List<ReconDiff> items);

    long countUnhandled();

    long countByBatchAndType(@Param("batchNo") String batchNo, @Param("diffType") int diffType);

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

    int clearAll();

}
