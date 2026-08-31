package com.dong.lab.crossborder.service;

import com.dong.lab.crossborder.dto.ComplianceRecordResponse;
import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.enums.ComplianceResult;

import java.math.BigDecimal;
import java.util.List;

/**
 * 合规筛查。跨境汇款在资金划转前必须依次过制裁名单、KYC、反洗钱和限额四道检查，
 * 任何一道不通过都不能放款，这是各国监管的硬性要求。
 *
 * <p>四道检查的中间件用法各有不同：
 * 制裁名单量大但判断简单，放 Redis Set 做 O(1) 匹配；
 * 日累计限额需要跨请求累加且必须原子，用 Lua 脚本保证累加与判断不可分；
 * KYC 与 AML 是纯规则判断，直接查账户属性即可。
 */
public interface ComplianceService {

    /**
     * 对一笔汇款做全量合规检查，逐条落库留痕。
     *
     * @return 最终结论，任一道 REJECT 则整体为 REJECT
     */
    ComplianceResult screen(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal sourceAmount);

    /**
     * 单独做制裁名单匹配。名单按国家维度分组，命中即拒绝。
     */
    boolean hitSanction(String country, String ownerName);

    /**
     * 加入制裁名单，用于演示与测试。
     */
    void addSanction(String ownerName);

    /**
     * 从制裁名单移除。
     */
    void removeSanction(String ownerName);

    long sanctionCount();

    /**
     * 检查并累加日累计限额。累加与判断必须原子完成，
     * 否则并发请求会各自读到旧值，导致日限额被突破。
     *
     * @return true 表示未超限额
     */
    boolean checkAndAccumulateDailyLimit(Long accountId, BigDecimal amount, BigDecimal dailyLimit);

    /**
     * 查询某笔汇款的合规记录。
     */
    List<ComplianceRecordResponse> recordsOf(String remittanceNo);

    /**
     * 重置某账户的日累计计数，仅用于实验环境清理。
     */
    void resetDailyUsage(Long accountId);

}
