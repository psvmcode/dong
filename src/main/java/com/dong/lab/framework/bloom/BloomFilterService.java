package com.dong.lab.framework.bloom;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BloomFilterService {

    private final RedissonClient redissonClient;

    public BloomFilterService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public <T> RBloomFilter<T> getOrCreate(String name, long expectedInsertions, double falsePositiveRate) {
        RBloomFilter<T> filter = redissonClient.getBloomFilter(name);
        filter.tryInit(expectedInsertions, falsePositiveRate);
        return filter;
    }

    public void delete(String name) {
        RBloomFilter<Object> filter = redissonClient.getBloomFilter(name);
        filter.delete();
    }

}
