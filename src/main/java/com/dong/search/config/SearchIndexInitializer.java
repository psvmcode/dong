package com.dong.search.config;

import com.dong.search.service.SearchIndexService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
/**
 * 启动期的索引引导。真正干活的是 SearchIndexService，
 * 这里只负责在应用起来时把别名准备好，保证后面所有读写都不会撞上「索引不存在」。
 *
 * <p>为什么放在启动期而不是等第一次写：等到第一次写入再建，
 * 建出来的就是动态映射——字段类型全错，分面聚合直接报错，而且改不回来。
 *
 * <p>引导失败直接抛异常让应用启动失败：索引准备不好等于整个 search 模块不可用，
 * 与其带着残缺状态启动、等第一次写入才报错，不如在启动期就暴露出来。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class SearchIndexInitializer {

    /**
     * searchIndexService，索引治理服务。
     */
    private final SearchIndexService searchIndexService;

    /**
     * 启动时准备好索引别名。
     */
    @PostConstruct
    public void prepareIndex() {
        searchIndexService.ensureAlias();
        log.info("search index ready, alias points to {}", searchIndexService.currentIndex());
    }

}
