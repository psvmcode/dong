package com.dong.lab.classic.service;

import java.util.Map;

/**
 * 分布式锁对照实验。
 *
 * <p>注意结果里区分 lockAcquired 与 lockTimedOut：
 * 拿不到锁的线程不会被静默计入丢失更新，否则实验结论会失真。
 */
public interface LockLabService {

    /**
     * 不加锁的并发自增，用作对照组。
     */
    Map<String, Object> withoutLock(int threads, int loops);

    /**
     * 加锁的并发自增。实现上必须用平台线程而非虚拟线程，
     * 因为 Redisson 可重入锁依赖线程 id 标识持有者，而虚拟线程的 id 不保证唯一。
     */
    Map<String, Object> withLock(int threads, int loops);

}
