package com.dong.ad.support;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 广告链路进程内计数。只用于运行时观察，不参与任何业务决策，
 * 因此全部用 LongAdder，避免把统计锁加到计费热路径上。
 */
@Component
@Getter
public class AdRuntimeStats {

    /**
     * 广告请求数。
     */
    private final LongAdder requests = new LongAdder();

    /**
     * 有胜出广告的请求数。
     */
    private final LongAdder matched = new LongAdder();

    /**
     * 有效上报事件数。
     */
    private final LongAdder events = new LongAdder();

    /**
     * 重复上报数。
     */
    private final LongAdder duplicates = new LongAdder();

    /**
     * 被无效流量或护栏过滤数。
     */
    private final LongAdder filtered = new LongAdder();

    /**
     * Redis 预算闸门降级到数据库的次数。
     */
    private final LongAdder redisFallbacks = new LongAdder();

    /**
     * 计费消息降级为同步的次数。
     */
    private final LongAdder mqFallbacks = new LongAdder();

    /**
     * 护栏拦下的事件数。
     */
    private final LongAdder rejected = new LongAdder();

    /**
     * 生成运行时快照。
     *
     * @return 计数快照
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("requests", requests.sum());
        snapshot.put("matched", matched.sum());
        snapshot.put("events", events.sum());
        snapshot.put("duplicates", duplicates.sum());
        snapshot.put("filtered", filtered.sum());
        snapshot.put("rejected", rejected.sum());
        snapshot.put("redisFallbacks", redisFallbacks.sum());
        snapshot.put("mqFallbacks", mqFallbacks.sum());
        return snapshot;
    }

}
