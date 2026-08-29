package com.dong.lab.framework.cache;

import com.dong.lab.common.util.JsonUtils;
import com.dong.lab.framework.redis.RedisService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Slf4j
public class CacheEventBus {

    @Getter
    private final String nodeId = UUID.randomUUID().toString().substring(0, 8);

    private final RedisService redisService;

    private final String channel;

    private final List<Consumer<CacheInvalidationEvent>> listeners = new CopyOnWriteArrayList<>();

    public CacheEventBus(RedisService redisService, String channel) {
        this.redisService = redisService;
        this.channel = channel;
    }

    public void register(Consumer<CacheInvalidationEvent> listener) {
        listeners.add(listener);
    }

    public String getChannel() {
        return channel;
    }

    public void publishInvalidation(String key) {
        CacheInvalidationEvent event = new CacheInvalidationEvent(key, nodeId, System.currentTimeMillis());
        redisService.publish(channel, JsonUtils.toJson(event));
    }

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
