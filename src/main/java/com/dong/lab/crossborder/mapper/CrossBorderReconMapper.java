package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.enums.SettlementChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/**
 * CrossBorderReconMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface CrossBorderReconMapper {

    /**
     * 查询某批次已结算的汇款单，作为对账的本地侧数据源。
     * 只取已结算的单子，未结算的不参与对账。
     */
    List<CrossBorderRemittance> selectSettledByBatch(@Param("batchNo") String batchNo,
                                                       @Param("status") RemittanceStatus status);

    /**
     * 查询某账户某时间段的贷方流水总额，用于与渠道回单的入账金额比对。
     */
    BigDecimal sumCreditByAccountAndTime(@Param("accountId") Long accountId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /**
     * 按清算渠道统计已结算的笔数与总额，生成渠道维度的对账汇总。
     */
    List<java.util.Map<String, Object>> summaryByChannel(@Param("from") LocalDateTime from,
                                                          @Param("to") LocalDateTime to,
                                                          @Param("status") RemittanceStatus status);

}
