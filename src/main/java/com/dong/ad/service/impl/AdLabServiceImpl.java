package com.dong.ad.service.impl;

import com.dong.ad.dto.AdEventRequest;
import com.dong.ad.dto.AdEventResponse;
import com.dong.ad.dto.BurstLabRequest;
import com.dong.ad.dto.BurstLabResponse;
import com.dong.ad.entity.AdCampaign;
import com.dong.ad.entity.AdDailyStat;
import com.dong.ad.enums.AdCampaignStatus;
import com.dong.ad.enums.AdChargeMode;
import com.dong.ad.enums.AdChargeStatus;
import com.dong.ad.enums.AdEventType;
import com.dong.ad.mapper.AdCampaignMapper;
import com.dong.ad.mapper.AdDailyStatMapper;
import com.dong.ad.service.AdDeliveryService;
import com.dong.ad.service.AdLabService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.util.Snowflake;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 并发计费对照实验。
 *
 * <p>实验跑的是完整的「先曝光、后点击」链路，并发打在点击上，
 * 因为 CPC 计划只有点击才扣钱，这正是预算竞争最激烈的一跳。
 * 曝光在预生成阶段串行上报，避免把令牌签发与曝光写库的耗时混进并发测量。
 *
 * <p>强制同步计费：异步消费何时落库不可控，回读数据库时会把「还没结算」
 * 误判成「没有超投」，测量结论就失真了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdLabServiceImpl implements AdLabService {

    /**
     * 共享工作线程上限。实验本身是压测工具，不能无限吃应用线程。
     */
    private static final int MAX_EXPERIMENT_THREADS = 64;

    /**
     * 线程名序号。
     */
    private final AtomicInteger threadSeq = new AtomicInteger();

    /**
     * adDeliveryService，业务服务层。
     */
    private final AdDeliveryService adDeliveryService;

    /**
     * adCampaignMapper，MyBatis Mapper 数据访问层。
     */
    private final AdCampaignMapper adCampaignMapper;

    /**
     * adDailyStatMapper，MyBatis Mapper 数据访问层。
     */
    private final AdDailyStatMapper adDailyStatMapper;

    /**
     * 雪花 ID 生成器，用于生成互不重复的事件幂等键。
     */
    private final Snowflake snowflake;

    /**
     * Redis 预算闸门开关，实验未指定 mode 时按当前配置取。
     */
    @Value("${dong.ad.redis-budget-enabled:true}")
    private boolean redisBudgetEnabled;

    /**
     * 同时允许运行的实验数。
     */
    @Value("${dong.ad.lab-max-concurrent-experiments:2}")
    private int maxConcurrentExperiments;

    /**
     * 全局实验闸门。
     */
    private Semaphore experimentGate;

    /**
     * 共享工作线程池，有界队列防止任务堆积吃光内存。
     */
    private ExecutorService executor;

    /**
     * 初始化闸门与共享线程池。@Value 在构造之后才注入，必须在 init 里构建。
     */
    @PostConstruct
    public void init() {
        experimentGate = new Semaphore(Math.max(1, Math.min(maxConcurrentExperiments, Constants.MAX_THREADS)));
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "ad-lab-" + threadSeq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        executor = new ThreadPoolExecutor(4, MAX_EXPERIMENT_THREADS, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Constants.MAX_BATCH_SIZE), factory, new ThreadPoolExecutor.AbortPolicy());
        log.info("ad lab executor initialized gate={} threads<={}", experimentGate.availablePermits(), MAX_EXPERIMENT_THREADS);
    }

    /**
     * 关闭共享线程池，避免上下文重启后残留线程。
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 执行并发对照实验。
     */
    @Override
    public BurstLabResponse burst(BurstLabRequest request) {
        long totalTasks = (long) request.getThreads() * request.getLoopsPerThread();
        if (totalTasks > Constants.MAX_BATCH_SIZE) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "threads * loopsPerThread exceeds " + Constants.MAX_BATCH_SIZE);
        }
        AdCampaign campaign = adCampaignMapper.selectById(request.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "campaign " + request.getCampaignId() + " not found");
        }
        if (campaign.getStatus() != AdCampaignStatus.DELIVERING) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "campaign must be delivering before burst lab");
        }
        AdChargeMode mode = request.getMode() == null || request.getMode().isBlank()
                ? (redisBudgetEnabled ? AdChargeMode.REDIS : AdChargeMode.DB)
                : AdChargeMode.ofName(request.getMode());
        int total = (int) totalTasks;
        Long baseUserId = request.getUserId() == null ? 9_000_000L : request.getUserId();
        List<String> traces = prepareTraces(campaign.getId(), baseUserId, total, mode);
        LongAdder accepted = new LongAdder();
        LongAdder duplicated = new LongAdder();
        LongAdder rejected = new LongAdder();
        LongAdder filtered = new LongAdder();
        LongAdder errors = new LongAdder();
        if (!experimentGate.tryAcquire()) {
            throw new BusinessException(Constants.CODE_TOO_MANY_REQUESTS, "too many concurrent ad experiments");
        }
        CountDownLatch latch = new CountDownLatch(total);
        long started = System.nanoTime();
        try {
            for (int index = 0; index < total; index++) {
                final int seq = index;
                executor.submit(() -> {
                    try {
                        AdEventResponse response = adDeliveryService.track(clickEvent(traces.get(seq), baseUserId + seq),
                                AdEventType.CLICK, mode, true);
                        classify(response, accepted, duplicated, rejected, filtered);
                    } catch (Exception ex) {
                        errors.increment();
                        log.warn("ad burst task failed seq={}", seq, ex);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            if (!latch.await(2L, TimeUnit.MINUTES)) {
                log.error("ad burst lab timed out before all tasks finished");
                errors.increment();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "burst lab interrupted");
        } catch (RuntimeException ex) {
            errors.increment();
            log.warn("ad burst lab submit failed", ex);
        } finally {
            while (latch.getCount() > 0L) {
                latch.countDown();
            }
            experimentGate.release();
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        BurstLabResponse response = new BurstLabResponse();
        response.setMode(mode.text());
        response.setThreads(request.getThreads());
        response.setLoopsPerThread(request.getLoopsPerThread());
        response.setTotal(total);
        response.setAccepted((int) accepted.sum());
        response.setDuplicated((int) duplicated.sum());
        response.setRejected((int) rejected.sum());
        response.setFiltered((int) filtered.sum());
        response.setErrors((int) errors.sum());
        response.setElapsedMillis(elapsedMillis);
        response.setAvgMillis(total == 0 ? 0D : (double) elapsedMillis / total);
        fillAuthority(response, campaign);
        log.info("ad burst lab finished mode={} total={} accepted={} rejected={} elapsed={}ms charged={}",
                mode, total, accepted.sum(), rejected.sum(), elapsedMillis, response.getDbChargeCents());
        return response;
    }

    /**
     * 预生成令牌并串行上报曝光。实验模式豁免最小点击间隔，
     * 因为并发点击与曝光几乎同时发生，线上那条 200ms 规则在这里只会把压测全部过滤掉。
     *
     * @param campaignId 计划 id
     * @param baseUserId 用户 id 起始值
     * @param total      任务数
     * @param mode       预算闸门路径
     * @return 链路 id 列表
     */
    private List<String> prepareTraces(Long campaignId, Long baseUserId, int total, AdChargeMode mode) {
        List<String> traces = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            long userId = (baseUserId == null ? 9_000_000L : baseUserId) + index;
            String traceId = adDeliveryService.issueTokenForLab(campaignId, userId);
            AdEventRequest impression = new AdEventRequest();
            impression.setTraceId(traceId);
            impression.setEventId("LAB-IMP-" + snowflake.nextIdStr());
            impression.setUserId(userId);
            impression.setIp("127.0.0.1");
            impression.setUserAgent("lab");
            adDeliveryService.track(impression, AdEventType.IMPRESSION, mode, true);
            traces.add(traceId);
        }
        return traces;
    }

    /**
     * 构造点击事件请求。
     *
     * @param traceId 链路 id
     * @param userId  用户 id
     * @return 事件请求
     */
    private AdEventRequest clickEvent(String traceId, long userId) {
        AdEventRequest request = new AdEventRequest();
        request.setTraceId(traceId);
        request.setEventId("LAB-CLK-" + snowflake.nextIdStr());
        request.setUserId(userId);
        request.setIp("127.0.0.1");
        request.setUserAgent("lab");
        return request;
    }

    /**
     * 按响应归类计数。
     *
     * @param response   事件响应
     * @param accepted   有效计数
     * @param duplicated 重放计数
     * @param rejected   护栏拒绝计数
     * @param filtered   无效流量计数
     */
    private void classify(AdEventResponse response, LongAdder accepted, LongAdder duplicated, LongAdder rejected, LongAdder filtered) {
        if (response.isDuplicate()) {
            duplicated.increment();
            return;
        }
        if (!response.isAccepted()) {
            filtered.increment();
            return;
        }
        if (AdChargeStatus.REJECTED.name().equals(response.getChargeStatus())) {
            rejected.increment();
            return;
        }
        accepted.increment();
    }

    /**
     * 回读权威流水填金额结论。只看接口成功数看不出超投，
     * 必须拿库里的今日已花费与日预算对比。
     *
     * @param response 实验响应
     * @param campaign 实验计划
     */
    private void fillAuthority(BurstLabResponse response, AdCampaign campaign) {
        AdDailyStat stat = adDailyStatMapper.selectByDateAndCampaign(LocalDate.now(), campaign.getId());
        long budget = stat == null ? campaign.getDailyBudgetCents() : stat.getBudgetCents();
        long charged = stat == null ? 0L : stat.getChargeCents();
        response.setDbChargeCents(charged);
        response.setBudgetCents(budget);
        response.setRejectedCents(stat == null ? 0L : stat.getRejectedCents());
        response.setOverspend(charged > budget);
        response.getNotes().put("authority", "ad_charge_record -> ad_daily_stat");
        response.getNotes().put("modeHint", "切换 mode=redis / mode=db 重跑同一批任务，对比 elapsedMillis 与 dbChargeCents");
        response.getNotes().put("budgetGuard", charged <= budget ? "no overspend" : "overspend detected, inspect reconcile details");
        if (charged == 0L) {
            response.getNotes().put("hint", "CPM 计划在曝光阶段扣费、并发阶段不扣费；做点击并发对照请用 CPC 计划");
        }
    }

}
