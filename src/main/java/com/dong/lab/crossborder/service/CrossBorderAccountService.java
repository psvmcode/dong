package com.dong.lab.crossborder.service;

import com.dong.lab.crossborder.dto.AccountCreateRequest;
import com.dong.lab.crossborder.dto.AccountResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨境账户服务。同一用户在不同币种下是不同账户，
 * 因为各币种资金分开清算，不能混在一个余额里。
 */
public interface CrossBorderAccountService {

    Long create(AccountCreateRequest request);

    AccountResponse findByAccountNo(String accountNo);

    AccountResponse findById(Long id);

    List<AccountResponse> findAll();

    /**
     * 校验余额与流水是否一致，用于验证记账正确性。
     *
     * @return 余额减去流水累计的差额，正常应为 0
     */
    BigDecimal balanceDiff(Long accountId, BigDecimal initialBalance);

    int clearAll();

}
