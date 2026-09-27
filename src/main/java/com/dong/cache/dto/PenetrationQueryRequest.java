package com.dong.cache.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 缓存穿透实验请求。
 *
 * <p>count 上限沿用批量上限：这个接口会在一次请求里循环发起这么多次查询，
 * 不封顶的话传一个极大值就能把整台机器的 CPU 占满。
 */
public class PenetrationQueryRequest {

    /**
     * 发起的请求次数。
     */
    @Min(1)
    @Max(Constants.MAX_BATCH_SIZE)
    private int count = 2000;

    /**
     * 是否走布隆过滤器前置拦截。
     */
    private boolean guarded = false;

    /**
     * 获取发起的请求次数。
     *
     * @return 请求次数
     */
    public int getCount() {
        return count;
    }

    /**
     * 设置发起的请求次数。
     *
     * @param count 请求次数
     */
    public void setCount(int count) {
        this.count = count;
    }

    /**
     * 是否走布隆过滤器前置拦截。
     *
     * @return 是否走布隆过滤器
     */
    public boolean isGuarded() {
        return guarded;
    }

    /**
     * 设置是否走布隆过滤器前置拦截。
     *
     * @param guarded 是否走布隆过滤器
     */
    public void setGuarded(boolean guarded) {
        this.guarded = guarded;
    }

}
