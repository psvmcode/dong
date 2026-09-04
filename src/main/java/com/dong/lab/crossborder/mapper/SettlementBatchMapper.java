package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.SettlementBatch;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.enums.SettlementStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
/**
 * SettlementBatchMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface SettlementBatchMapper {

    /**
     * 按 BatchNo 查询记录。
     */
    SettlementBatch selectByBatchNo(String batchNo);

    /**
     * 按 Status 查询记录。
     */
    List<SettlementBatch> selectByStatus(@Param("status") SettlementStatus status, @Param("limit") int limit);

    /**
     * 查询所有记录。
     */
    List<SettlementBatch> selectAll();

    /**
     * 查询某渠道某币种当前打开的批次。实时清算的汇款单入账时自动归入，
     * 保证每笔已结算的单子都有批次归属，否则按批次对账会漏掉它们。
     */
    SettlementBatch selectOpenByChannelAndCurrency(@Param("channel") SettlementChannel channel,
                                                   @Param("currency") String currency);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(SettlementBatch batch);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("batchNo") String batchNo, @Param("status") SettlementStatus status);

    /**
     * 更新批次总金额与总笔数。
     */
    int updateTotal(@Param("batchNo") String batchNo,
                    @Param("totalCount") int totalCount,
                    @Param("totalAmount") java.math.BigDecimal totalAmount);

    /**
     * 关闭到期的批次，由定时任务按清算截止时间触发。
     */
    int closeOverdue(@Param("now") LocalDateTime now);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
