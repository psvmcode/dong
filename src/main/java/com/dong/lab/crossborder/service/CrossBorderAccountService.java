package com.dong.lab.crossborder.service;

import com.dong.lab.crossborder.dto.AccountCreateRequest;
import com.dong.lab.crossborder.dto.AccountEventResponse;
import com.dong.lab.crossborder.dto.AccountResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨境账户服务。同一用户在不同币种下是不同账户，
 * 因为各币种资金分开清算，不能混在一个余额里。
 */
public interface CrossBorderAccountService {

    /**
     * 创建记录。
     */
    Long create(AccountCreateRequest request);

    /**
     * findByAccountNo。
     */
    AccountResponse findByAccountNo(String accountNo);

    /**
     * 根据 id 查询。
     */
    AccountResponse findById(Long id);

    /**
     * 查询全部。
     */
    List<AccountResponse> findAll();

    /**
     * 冻结账户。反洗钱调查或司法冻结时调用，冻结后账户不能发起新汇款，
     * 已有余额与流水完整保留。每次冻结落一条事件记录。
     */
    AccountResponse freeze(String accountNo, String reason, String operator);

    /**
     * 解冻账户。与冻结对称，同样落事件记录，保证状态变化全程可追溯。
     */
    AccountResponse unfreeze(String accountNo, String reason, String operator);

    /**
     * 查询账户事件历史，按时间正序返回。
     */
    List<AccountEventResponse> events(String accountNo);

    /**
     * 校验余额与流水是否一致，用于验证记账正确性。
     *
     * @return 余额减去流水累计的差额，正常应为 0
     */
    BigDecimal balanceDiff(Long accountId, BigDecimal initialBalance);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
