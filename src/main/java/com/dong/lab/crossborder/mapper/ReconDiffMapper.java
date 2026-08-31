package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.ReconDiff;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReconDiffMapper {

    List<ReconDiff> selectByBatchNo(String batchNo);

    List<ReconDiff> selectAll(@Param("limit") int limit);

    int insert(ReconDiff diff);

    long countUnhandled();

    int markHandled(@Param("batchNo") String batchNo);

    int clearAll();

}
