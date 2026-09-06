package com.dong.crossborder.service;

import java.util.List;
import java.util.Map;

/**
 * 反洗钱交易监控。
 *
 * <p>拆分交易（structuring）是反洗钱最经典的规避手法：
 * 把一笔要申报的大额故意拆成多笔刚好低于申报线的汇款，
 * 例如把 30000 美元拆成三笔 9999 美元，逃避大额交易报告。
 *
 * <p>检测思路不是看单笔金额，而是看行为模式：
 * 同一付款人在短窗口内的累计金额达到申报线，且每一笔都紧贴申报线下方，
 * 即构成拆分嫌疑。单独看任何一笔都完全合规，这正是它的危害所在。
 */
public interface AmlMonitor {

    /**
     * 记录一笔汇款并检测是否构成拆分模式。
     *
     * @param payerAccountId 付款方账户
     * @param amount         本笔金额
     * @return 命中时返回嫌疑描述，未命中返回空
     */
    java.util.Optional<String> detectStructuring(Long payerAccountId, java.math.BigDecimal amount);

    /**
     * 某账户当日的小额交易笔数与累计金额。
     */
    Map<String, Object> structuringProfile(Long payerAccountId);

    /**
     * 命中拆分模式的账户列表，供风控复查。
     */
    List<Map<String, Object>> flaggedAccounts();

    /**
     * 清空监控数据，仅实验环境使用。
     */
    void reset();

}
