package com.dong.crossborder.service.impl;

import com.dong.common.constant.Constants;
import com.dong.crossborder.dto.ComplianceRecordResponse;
import com.dong.crossborder.entity.ComplianceRecord;
import com.dong.crossborder.entity.CrossBorderAccount;
import com.dong.crossborder.entity.CrossBorderRemittance;
import com.dong.crossborder.enums.ComplianceCheckType;
import com.dong.crossborder.enums.ComplianceResult;
import com.dong.crossborder.mapper.ComplianceRecordMapper;
import com.dong.crossborder.service.ComplianceService;
import com.dong.framework.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
/**
 * 合规筛查实现。
 *
 * <p>日累计限额的原子性是关键：先用 Lua 把金额累加，再判断是否超限，
 * 两步在同一个脚本内完成，因此并发请求不会各自读到旧值。
 * 若拆成两次 Redis 调用，中间存在竞态窗口，日限额会被突破。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ComplianceServiceImpl implements ComplianceService {

    private static final String SANCTION_KEY = "lab:crossborder:sanction";

    private static final String DAILY_USAGE_KEY = "lab:crossborder:daily:";

    /**
     * 累加并检查限额。返回 1 表示通过，0 表示超限。
     * 金额以分为单位传给脚本，避免 Lua 里的浮点精度问题。
     */
    private static final String LIMIT_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local delta = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local next = current + delta
            redis.call('SET', KEYS[1], tostring(next))
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            if next <= limit then
                return 1
            end
            return 0
            """;

    /**
     * 释放占用。用 math.max 兜底防止减成负数，
     * 因为扣款失败与退款可能重复触发释放。
     */
    private static final String RELEASE_SCRIPT = """
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local delta = tonumber(ARGV[1])
            local next = math.max(0, current - delta)
            redis.call('SET', KEYS[1], tostring(next))
            return next
            """;

    private static final RedisScript<Long> LIMIT = new DefaultRedisScript<>(LIMIT_SCRIPT, Long.class);

    private static final RedisScript<Long> RELEASE = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    /**
     * 超过该金额进入人工审核，这是反洗钱的常见做法。
     */
    private static final BigDecimal MANUAL_REVIEW_THRESHOLD = new BigDecimal("50000");

    /**
     * 日累计计数的过期时间，跨天自动清零。
     */
    private static final long DAILY_USAGE_TTL_SECONDS = 48 * 3600L;

    /**
     * complianceRecordMapper，MyBatis Mapper 数据访问层。
     */
    private final ComplianceRecordMapper complianceRecordMapper;

    /**
     * redisService，业务服务层。
     */
    private final RedisService redisService;

    /**
     * screen。
     */
    @Override
    public ComplianceResult screen(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal sourceAmount) {
        List<ComplianceResult> results = new ArrayList<>();
        ComplianceResult sanction = checkSanction(remittance, payer);
        results.add(sanction);
        ComplianceResult kyc = checkKyc(remittance, payer, sourceAmount);
        results.add(kyc);
        ComplianceResult aml = checkAml(remittance, sourceAmount);
        results.add(aml);
        ComplianceResult limit = checkLimit(remittance, payer, sourceAmount);
        results.add(limit);
        if (results.contains(ComplianceResult.REJECT)) {
            return ComplianceResult.REJECT;
        }
        if (results.contains(ComplianceResult.MANUAL_REVIEW)) {
            return ComplianceResult.MANUAL_REVIEW;
        }
        return ComplianceResult.PASS;
    }

    /**
     * 制裁名单匹配。名单放 Redis Set，会员判断是 O(1)，
     * 适合每笔交易都要查的高频场景。
     */
    private ComplianceResult checkSanction(CrossBorderRemittance remittance, CrossBorderAccount payer) {
        boolean hit = hitSanction(payer.getCountry(), payer.getOwnerName());
        record(remittance.getRemittanceNo(), ComplianceCheckType.SANCTION,
                hit ? ComplianceResult.REJECT : ComplianceResult.PASS,
                hit ? "sanction list matched " + payer.getOwnerName() : "");
        return hit ? ComplianceResult.REJECT : ComplianceResult.PASS;
    }

    /**
     * KYC 等级校验。真实系统里不同等级对应不同的可汇额度，
     * 未认证账户只能做小额，这是反洗钱的基本要求。
     */
    private ComplianceResult checkKyc(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal amount) {
        Integer level = payer.getKycLevel() == null ? 0 : payer.getKycLevel();
        BigDecimal allowed = switch (level) {
            case 0 -> new BigDecimal("1000");
            case 1 -> new BigDecimal("10000");
            case 2 -> new BigDecimal("100000");
            default -> new BigDecimal("10000000");
        };
        boolean pass = amount.compareTo(allowed) <= 0;
        record(remittance.getRemittanceNo(), ComplianceCheckType.KYC,
                pass ? ComplianceResult.PASS : ComplianceResult.REJECT,
                pass ? "" : "kyc level " + level + " allows at most " + allowed);
        return pass ? ComplianceResult.PASS : ComplianceResult.REJECT;
    }

    /**
     * 反洗钱检查。超过阈值挂人工审核而不是直接拒绝，
     * 因为大额交易本身合法，只是需要人工确认来源与用途。
     */
    private ComplianceResult checkAml(CrossBorderRemittance remittance, BigDecimal amount) {
        boolean needReview = amount.compareTo(MANUAL_REVIEW_THRESHOLD) > 0;
        record(remittance.getRemittanceNo(), ComplianceCheckType.AML,
                needReview ? ComplianceResult.MANUAL_REVIEW : ComplianceResult.PASS,
                needReview ? "amount exceeds manual review threshold " + MANUAL_REVIEW_THRESHOLD : "");
        return needReview ? ComplianceResult.MANUAL_REVIEW : ComplianceResult.PASS;
    }

    /**
     * 限额检查。单笔限额直接比对，日累计限额走 Lua 原子累加。
     */
    private ComplianceResult checkLimit(CrossBorderRemittance remittance, CrossBorderAccount payer, BigDecimal amount) {
        BigDecimal singleLimit = payer.getSingleLimit() == null
                ? new BigDecimal("1000000") : payer.getSingleLimit();
        if (amount.compareTo(singleLimit) > 0) {
            record(remittance.getRemittanceNo(), ComplianceCheckType.LIMIT, ComplianceResult.REJECT,
                    "single amount exceeds limit " + singleLimit);
            return ComplianceResult.REJECT;
        }

        BigDecimal dailyLimit = payer.getDailyLimit() == null
                ? new BigDecimal("1000000") : payer.getDailyLimit();
        boolean within = checkAndAccumulateDailyLimit(payer.getId(), amount, dailyLimit);
        record(remittance.getRemittanceNo(), ComplianceCheckType.LIMIT,
                within ? ComplianceResult.PASS : ComplianceResult.REJECT,
                within ? "" : "daily limit " + dailyLimit + " exceeded");
        return within ? ComplianceResult.PASS : ComplianceResult.REJECT;
    }

    /**
     * 制裁名单匹配。Redis 不可用时返回 true（宁可误拒不可漏放），
     * 因为漏放一个制裁对象的后果远比误拒一笔正常汇款严重。
     */
    @Override
    public boolean hitSanction(String country, String ownerName) {
        try {
            Boolean member = redisService.template().opsForSet().isMember(SANCTION_KEY, ownerName);
            return Boolean.TRUE.equals(member);
        } catch (Exception ex) {
            log.error("sanction list check failed, fail safe by treating as hit name={}", ownerName, ex);
            return true;
        }
    }

    /**
     * addSanction。
     */
    @Override
    public void addSanction(String ownerName) {
        redisService.template().opsForSet().add(SANCTION_KEY, ownerName);
    }

    /**
     * removeSanction。
     */
    @Override
    public void removeSanction(String ownerName) {
        redisService.template().opsForSet().remove(SANCTION_KEY, ownerName);
    }

    /**
     * sanctionCount。
     */
    @Override
    public long sanctionCount() {
        Long size = redisService.template().opsForSet().size(SANCTION_KEY);
        return size == null ? 0L : size;
    }

    /**
     * checkAndAccumulateDailyLimit。
     */
    @Override
    public boolean checkAndAccumulateDailyLimit(Long accountId, BigDecimal amount, BigDecimal dailyLimit) {
        String key = DAILY_USAGE_KEY + LocalDate.now() + ":" + accountId;
        long cents = amount.multiply(new BigDecimal("100")).longValue();
        long limitCents = dailyLimit.multiply(new BigDecimal("100")).longValue();
        Long result = redisService.execute(LIMIT, List.of(key), cents, limitCents, DAILY_USAGE_TTL_SECONDS);
        if (result == null) {
            log.warn("daily limit script returned nothing for account {}, rejecting", accountId);
            return false;
        }
        return result > 0L;
    }

    /**
     * releaseDailyLimit。
     */
    @Override
    public void releaseDailyLimit(Long accountId, BigDecimal amount) {
        String key = DAILY_USAGE_KEY + LocalDate.now() + ":" + accountId;
        long cents = amount.multiply(new BigDecimal("100")).longValue();
        redisService.execute(RELEASE, List.of(key), cents);
    }

    /**
     * recordsOf。
     */
    @Override
    public List<ComplianceRecordResponse> recordsOf(String remittanceNo) {
        return complianceRecordMapper.selectByRemittanceNo(remittanceNo).stream()
                .map(ComplianceRecordResponse::from)
                .toList();
    }

    /**
     * 人工复核结论落库。detail 里带上审核人与备注，
     * 监管检查时这行记录就是「该笔大额交易经过了人工确认」的直接证据。
     */
    @Override
    public void recordManualDecision(String remittanceNo, ComplianceResult result, String detail) {
        record(remittanceNo, ComplianceCheckType.MANUAL_REVIEW, result,
                detail == null || detail.isBlank() ? "no detail" : detail);
    }

    /**
     * resetDailyUsage。
     */
    @Override
    public void resetDailyUsage(Long accountId) {
        redisService.delete(DAILY_USAGE_KEY + LocalDate.now() + ":" + accountId);
    }

    /**
     * record。
     */
    private void record(String remittanceNo, ComplianceCheckType type, ComplianceResult result, String detail) {
        ComplianceRecord record = new ComplianceRecord();
        record.setRemittanceNo(remittanceNo);
        record.setCheckType(type);
        record.setResult(result);
        record.setHitDetail(detail == null ? Constants.MESSAGE_SUCCESS : detail);
        complianceRecordMapper.insert(record);
    }

}
