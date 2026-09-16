package com.dong.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
/**
 * 索引名解析器。项目里所有索引名都不写死，统一经这里补前缀，
 * 真实索引名固定是「前缀_逻辑名」，例如 product 最终落到 dong_lab_product。
 *
 * <p>为什么要前缀：ES 集群一旦被多套环境或多个应用共用，
 * product、order 这种通用名字必然撞车；加前缀相当于给本项目的索引划一块独立命名空间，
 * 换前缀就能在同一集群里并存两套索引，互不干扰。
 *
 * <p>前缀来自 dong.elasticsearch.index-prefix，默认 dong_lab。
 * 改前缀就等于指向一套全新索引：项目里没用别名，旧索引不会跟过来，历史数据要自己 reindex。
 *
 * <p>调用方只有两处：SearchIndexInitializer 按它建索引，
 * SearchServiceImpl 的写入、删除、检索、聚合都走它，索引名的拼法只有这一个出处。
 */
@Component
public class IndexNameResolver {

    /**
     * 索引名前缀，取自 dong.elasticsearch.index-prefix。
     */
    private final String prefix;

    public IndexNameResolver(@Value("${dong.elasticsearch.index-prefix:dong_lab}") String prefix) {
        this.prefix = prefix;
    }

    /**
     * 把逻辑索引名补全成真实索引名。
     *
     * @param name 逻辑索引名，如 product
     * @return 真实索引名，如 dong_lab_product
     */
    public String resolve(String name) {
        return prefix + "_" + name;
    }

}
