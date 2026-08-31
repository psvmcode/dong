package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.enums.LedgerDirection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AccountLedgerMapper {

    AccountLedger selectByLedgerNo(String ledgerNo);

    List<AccountLedger> selectByRemittanceNo(String remittanceNo);

    List<AccountLedger> selectByAccountId(@Param("accountId") Long accountId, @Param("limit") int limit);

    int insert(AccountLedger ledger);

    /**
     * 统计某账户某方向的流水总额，用于核对余额与流水是否一致。
     */
    BigDecimal sumByAccountAndDirection(@Param("accountId") Long accountId,
                                        @Param("direction") LedgerDirection direction);

    int clearAll();

}
