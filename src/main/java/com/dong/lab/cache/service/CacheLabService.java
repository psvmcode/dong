package com.dong.lab.cache.service;

import java.util.Map;

/**
 * CacheLabService，业务服务接口。
 */
public interface CacheLabService {

    /**
     * 缓存穿透对照实验。
     *
     * @param count   请求次数
     * @param guarded true 走布隆过滤器前置拦截，false 只靠缓存空值标记
     * @return 请求数、被拒数、耗时、被拦截次数、所用模式
     */
    Map<String, Object> penetration(int count, boolean guarded);

}
