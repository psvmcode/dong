package com.dong.lab.framework.bloom;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
/**
 * 基于 Redisson 的布隆过滤器，用来在查询前挡掉根本不可能存在的 id。
 *
 * <p>使用要点：
 * <ul>
 *     <li>布隆过滤器只会误判「存在」，不会误判「不存在」，所以挡掉的 id 一定是不存在的</li>
 *     <li>必须先预热，把真实 id 全部 add 进去，否则过滤器为空会导致所有请求都被拒绝</li>
 *     <li>误判率越低占用内存越大，需要按数据量权衡</li>
 * </ul>
 */
@Service
@Slf4j

public class BloomFilterService {

    /**
     * redissonClient。
     */
    private final RedissonClient redissonClient;

    public BloomFilterService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 获取过滤器，不存在则按预期容量和误判率初始化。
     * tryInit 是幂等的，重复调用不会重建已有数据。
     */
    public <T> RBloomFilter<T> getOrCreate(String name, long expectedInsertions, double falsePositiveRate) {
        RBloomFilter<T> filter = redissonClient.getBloomFilter(name);
        filter.tryInit(expectedInsertions, falsePositiveRate);
        return filter;
    }

    /**
     * 删除关注关系。
     */
    public void delete(String name) {
        RBloomFilter<Object> filter = redissonClient.getBloomFilter(name);
        filter.delete();
    }

}
