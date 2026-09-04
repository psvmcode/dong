package com.dong.lab.order.dto;
/**
 * 并发实验响应。看 successCount 与 attemptLogCount 的差值就能判断防护是否生效：
 * 有防护时两者应当分别是 1 和 threads，无防护时会一起变成 threads。
 */
public class OrderBenchmarkResponse {

    /**
     * 参与实验的订单号。
     */
    private String orderNo;

    /**
     * 并发线程数。
     */
    private Integer threads;

    /**
     * 实验模式，cas 表示乐观锁防护，none 表示不做防护。
     */
    private String mode;

    /**
     * 推进成功的次数。
     */
    private Integer successCount;

    /**
     * 被拦下的次数，含状态机拒绝与乐观锁冲突。
     */
    private Integer blockedCount;

    /**
     * 实验结束后的订单状态名。
     */
    private String finalStatus;

    /**
     * 乐观锁版本号，有防护时应当恰好加 1。
     */
    private Integer finalVersion;

    /**
     * 流转日志总条数，等于实际尝试次数。
     */
    private Integer attemptLogCount;

    /**
     * 耗时毫秒数。
     */
    private Long elapsedMs;

    /**
     * 获取订单号。
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * 设置订单号。
     *
     * @param orderNo 订单号
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * 获取并发线程数。
     */
    public Integer getThreads() {
        return threads;
    }

    /**
     * 设置并发线程数。
     *
     * @param threads 并发线程数
     */
    public void setThreads(Integer threads) {
        this.threads = threads;
    }

    /**
     * 获取实验模式。
     */
    public String getMode() {
        return mode;
    }

    /**
     * 设置实验模式。
     *
     * @param mode 实验模式
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 获取推进成功次数。
     */
    public Integer getSuccessCount() {
        return successCount;
    }

    /**
     * 设置推进成功次数。
     *
     * @param successCount 推进成功次数
     */
    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    /**
     * 获取被拦下次数。
     */
    public Integer getBlockedCount() {
        return blockedCount;
    }

    /**
     * 设置被拦下次数。
     *
     * @param blockedCount 被拦下次数
     */
    public void setBlockedCount(Integer blockedCount) {
        this.blockedCount = blockedCount;
    }

    /**
     * 获取实验结束后的状态名。
     */
    public String getFinalStatus() {
        return finalStatus;
    }

    /**
     * 设置实验结束后的状态名。
     *
     * @param finalStatus 状态名
     */
    public void setFinalStatus(String finalStatus) {
        this.finalStatus = finalStatus;
    }

    /**
     * 获取实验结束后的版本号。
     */
    public Integer getFinalVersion() {
        return finalVersion;
    }

    /**
     * 设置实验结束后的版本号。
     *
     * @param finalVersion 版本号
     */
    public void setFinalVersion(Integer finalVersion) {
        this.finalVersion = finalVersion;
    }

    /**
     * 获取流转日志总条数。
     */
    public Integer getAttemptLogCount() {
        return attemptLogCount;
    }

    /**
     * 设置流转日志总条数。
     *
     * @param attemptLogCount 日志条数
     */
    public void setAttemptLogCount(Integer attemptLogCount) {
        this.attemptLogCount = attemptLogCount;
    }

    /**
     * 获取耗时毫秒数。
     */
    public Long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * 设置耗时毫秒数。
     *
     * @param elapsedMs 耗时毫秒数
     */
    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

}
