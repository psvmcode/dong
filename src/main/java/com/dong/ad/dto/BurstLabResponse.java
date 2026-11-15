package com.dong.ad.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 并发计费对照实验结果。
 *
 * <p>除了耗时，还回读数据库里的今日已花费与预算作对比：
 * 只看「成功数」看不出超投，必须拿权威源的数字验证金额守恒。
 */
@Data
public class BurstLabResponse {

    /**
     * 实际使用的预算闸门路径
     */
    private String mode;

    /**
     * 并发线程数
     */
    private int threads;

    /**
     * 每线程循环次数
     */
    private int loopsPerThread;

    /**
     * 总任务数
     */
    private int total;

    /**
     * 被接受为有效事件数
     */
    private int accepted;

    /**
     * 重复上报数
     */
    private int duplicated;

    /**
     * 被护栏拒绝数
     */
    private int rejected;

    /**
     * 被无效流量过滤数
     */
    private int filtered;

    /**
     * 异常数
     */
    private int errors;

    /**
     * 总耗时，单位毫秒
     */
    private long elapsedMillis;

    /**
     * 单笔平均耗时，单位毫秒
     */
    private double avgMillis;

    /**
     * 数据库今日已计费金额，单位分
     */
    private long dbChargeCents;

    /**
     * 日预算，单位分
     */
    private long budgetCents;

    /**
     * 是否出现超投
     */
    private boolean overspend;

    /**
     * 被护栏拦下的金额，单位分
     */
    private long rejectedCents;

    /**
     * 结论与提示
     */
    private Map<String, Object> notes = new LinkedHashMap<>();

}
