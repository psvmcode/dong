package com.dong.rpc.task;

import com.dong.rpc.registry.ServiceRegistry;
import com.dong.rpc.support.ServiceExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 注册中心心跳。
 *
 * <p>注册不是一次性的：进程被 kill -9 时没机会执行注销，
 * 注册中心里就会留下一个永远存在、却永远连不上的地址。
 * 带 TTL 的续约把「实例已死」这个问题变成「最多错一个 TTL」，
 * 而 TTL 就是可用性与及时性之间的取舍点——设短了发现快，Redis 压力大；设长了故障实例摘得慢。
 *
 * <p>用 fixedDelay 而不是 fixedRate：上一轮没跑完就不开下一轮，
 * Redis 卡住时不会越堆越多。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class RegistryHeartbeatTask {

    /**
     * registry，注册中心。
     */
    private final ServiceRegistry registry;

    /**
     * exporter，服务暴露器，提供本进程已暴露的实例。
     */
    private final ServiceExporter exporter;

    /**
     * 续约一次。
     */
    @Scheduled(fixedDelayString = "${dong.rpc.heartbeat-interval-ms:10000}", initialDelayString = "${dong.rpc.heartbeat-initial-delay-ms:5000}")
    public void heartbeat() {
        try {
            int renewed = registry.renew(exporter.instances());
            if (renewed > 0) {
                log.debug("rpc registry renewed {} instances", renewed);
            }
        } catch (Exception ex) {
            // 调度线程会吞掉异常，不打日志的话任务就是「静默地不再工作」
            log.warn("rpc heartbeat failed: {}", ex.getMessage());
        }
    }

}
