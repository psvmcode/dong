package com.dong.crossborder.mapper;

import com.dong.crossborder.entity.AccountLedger;
import com.dong.crossborder.enums.LedgerDirection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
/**
 * AccountLedgerMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface AccountLedgerMapper {

    /**
     * 按 LedgerNo 查询记录。
     */
    AccountLedger selectByLedgerNo(String ledgerNo);

    /**
     * 按 RemittanceNo 查询记录。
     */
    List<AccountLedger> selectByRemittanceNo(String remittanceNo);

    /**
     * 按 AccountId 查询记录。
     */
    List<AccountLedger> selectByAccountId(@Param("accountId") Long accountId, @Param("limit") int limit);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(AccountLedger ledger);

    /**
     * 统计某账户某方向的流水总额，用于核对余额与流水是否一致。
     */
    BigDecimal sumByAccountAndDirection(@Param("accountId") Long accountId,
                                        @Param("direction") LedgerDirection direction);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
