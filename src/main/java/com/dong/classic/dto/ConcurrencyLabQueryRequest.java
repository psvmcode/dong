package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 并发自增实验请求。加锁与不加锁两组实验共用同一套参数，
 * 这样两者的耗时才有可比性。
 */
public class ConcurrencyLabQueryRequest {

    /**
     * 并发线程数。
     */
    @Min(1)
    @Max(Constants.MAX_THREADS)
    private int threads = 16;

    /**
     * 每个线程循环次数。与线程数相乘才是实际任务量，
     * 两个参数都必须限制，只限制一个等于没限制。
     */
    @Min(1)
    @Max(Constants.MAX_LOOPS)
    private int loops = 20;

    /**
     * 获取并发线程数。
     *
     * @return 线程数
     */
    public int getThreads() {
        return threads;
    }

    /**
     * 设置并发线程数。
     *
     * @param threads 线程数
     */
    public void setThreads(int threads) {
        this.threads = threads;
    }

    /**
     * 获取每个线程循环次数。
     *
     * @return 循环次数
     */
    public int getLoops() {
        return loops;
    }

    /**
     * 设置每个线程循环次数。
     *
     * @param loops 循环次数
     */
    public void setLoops(int loops) {
        this.loops = loops;
    }

}
