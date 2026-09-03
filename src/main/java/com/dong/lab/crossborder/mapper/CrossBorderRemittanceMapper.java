package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CrossBorderRemittanceMapper {

    CrossBorderRemittance selectByRemittanceNo(String remittanceNo);

    CrossBorderRemittance selectByIdempotentKey(String idempotentKey);

    List<CrossBorderRemittance> selectByStatus(@Param("status") RemittanceStatus status, @Param("limit") int limit);

    List<CrossBorderRemittance> selectByBatchNo(String batchNo);

    List<CrossBorderRemittance> selectPage(@Param("status") RemittanceStatus status,
                                           @Param("offset") int offset,
                                           @Param("size") int size);

    long countByStatus(@Param("status") RemittanceStatus status);

    /**
     * 按状态分组统计。一次性取回全部状态的计数，
     * 比逐个状态各查一次省去多轮往返。
     */
    List<java.util.Map<String, Object>> countGroupByStatus();

    int insert(CrossBorderRemittance remittance);

    /**
     * 推进状态。带 version 条件形成乐观锁，并发推进时只有一个能成功，
     * 失败方需要重新读取最新状态再决定下一步，不能直接覆盖。
     */
    int updateStatus(@Param("remittanceNo") String remittanceNo,
                     @Param("status") RemittanceStatus status,
                     @Param("expectedStatus") RemittanceStatus expectedStatus,
                     @Param("version") int version);

    int updateBatchNo(@Param("remittanceNo") String remittanceNo, @Param("batchNo") String batchNo);

    /**
     * 审核放行后回填成交要素：锁定汇率、手续费、目标金额与合规结论。
     * 挂起时这些字段还是占位的 0，锁汇完成后才有真实值。
     * 不带 version 条件：调用前已用 updateStatus 独占抢占，
     * 只有抢占成功的请求能走到这里，天然无并发写。
     */
    int updateSettlementTerms(@Param("remittanceNo") String remittanceNo,
                              @Param("exchangeRate") java.math.BigDecimal exchangeRate,
                              @Param("feeAmount") java.math.BigDecimal feeAmount,
                              @Param("targetAmount") java.math.BigDecimal targetAmount,
                              @Param("complianceStatus") int complianceStatus);

    int updateFailReason(@Param("remittanceNo") String remittanceNo,
                         @Param("status") RemittanceStatus status,
                         @Param("failReason") String failReason);

    int updateQuoteNo(@Param("remittanceNo") String remittanceNo, @Param("quoteNo") String quoteNo);

    int clearAll();

}
