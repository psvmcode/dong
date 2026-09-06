package com.dong.framework.cache;

import com.dong.common.util.JsonUtils;
import com.dong.framework.redis.RedisService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
/**
 * 失效事件总线，基于 Redis 发布订阅，把失效广播给所有节点。
 *
 * <p>本地缓存无法跨节点感知失效，只能靠这个机制兜底。
 * 注意事件必须带来源节点并做过滤，否则自己发的消息又触发自己清理一遍。
 */
@Slf4j

public class CacheEventBus {

    @Getter
    private final String nodeId = UUID.randomUUID().toString().substring(0, 8);

    /**
     * Redis 服务。
     */
    private final RedisService redisService;

    /**
     * 失效广播频道。
     */
    private final String channel;

    private final List<Consumer<CacheInvalidationEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 构造缓存事件总线。
     *
     * @param redisService Redis 服务
     * @param channel      失效广播频道
     */
    public CacheEventBus(RedisService redisService, String channel) {
        this.redisService = redisService;
        this.channel = channel;
    }

    /**
     * 注册失效事件监听器。
     *
     * @param listener 监听器
     */
    public void register(Consumer<CacheInvalidationEvent> listener) {
        listeners.add(listener);
    }

    /**
     * 获取失效广播频道。
     *
     * @return 频道名称
     */
    public String getChannel() {
        return channel;
    }

    /**
     * 发布缓存失效事件。
     *
     * @param key 缓存键
     */
    public void publishInvalidation(String key) {
        CacheInvalidationEvent event = new CacheInvalidationEvent(key, nodeId, System.currentTimeMillis());
        redisService.publish(channel, JsonUtils.toJson(event));
    }

    /**
     * 收到事件。先过滤自己发出的，再分发给本地监听器，
     * 单个监听器失败不影响其他监听器。
     */
    public void dispatch(String payload) {
        CacheInvalidationEvent event = JsonUtils.fromJson(payload, CacheInvalidationEvent.class);
        if (nodeId.equals(event.sourceNode())) {
            return;
        }
        listeners.forEach(listener -> {
            try {
                listener.accept(event);
            } catch (Exception ex) {
                log.error("cache invalidation listener failed for key={}", event.key(), ex);
            }
        });
    }

}
