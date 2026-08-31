package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.SettlementBatch;
import com.dong.lab.crossborder.enums.SettlementStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SettlementBatchMapper {

    SettlementBatch selectByBatchNo(String batchNo);

    List<SettlementBatch> selectByStatus(@Param("status") SettlementStatus status, @Param("limit") int limit);

    List<SettlementBatch> selectAll();

    int insert(SettlementBatch batch);

    int updateStatus(@Param("batchNo") String batchNo, @Param("status") SettlementStatus status);

    int updateTotal(@Param("batchNo") String batchNo,
                    @Param("totalCount") int totalCount,
                    @Param("totalAmount") java.math.BigDecimal totalAmount);

    /**
     * 关闭到期的批次，由定时任务按清算截止时间触发。
     */
    int closeOverdue(@Param("now") LocalDateTime now);

    int clearAll();

}
