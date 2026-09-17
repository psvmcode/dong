package com.dong.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 索引名解析器。项目里所有索引名都不写死，统一经这里拼出来。
 *
 * <p>这里解析出来的名字其实是「别名」，不是真实索引。真实索引带版本号
 * （dong_lab_product_v1、v2……），由索引治理服务在别名背后换来换去，
 * 应用代码只认别名，所以重建索引既不用改代码也不用停服务。
 *
 * <p>为什么要前缀：ES 集群一旦被多套环境或多个应用共用，
 * product、order 这种通用名字必然撞车；加前缀相当于给本项目的索引划一块独立命名空间。
 *
 * <p>前缀来自 dong.elasticsearch.index-prefix，默认 dong_lab。
 * 改前缀就等于指向一套全新索引：旧索引不会跟过来，历史数据要自己 reindex。
 */
@Component
public class IndexNameResolver {

    /**
     * 商品索引的逻辑名。全项目只有这一处定义，别的地方都引用它，
     * 免得改前缀时漏掉某处手写的字符串。
     */
    public static final String PRODUCT_INDEX = "product";

    /**
     * 版本号后缀，用于从真实索引名里反解析版本。
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("_v(\\d+)$");

    /**
     * 索引名前缀，取自 dong.elasticsearch.index-prefix。
     */
    private final String prefix;

    public IndexNameResolver(@Value("${dong.elasticsearch.index-prefix:dong_lab}") String prefix) {
        this.prefix = prefix;
    }

    /**
     * 对外的索引名，也就是别名。读写都用它，业务代码里不该出现版本号。
     *
     * @param name 逻辑索引名，如 product
     * @return 别名，如 dong_lab_product
     */
    public String resolve(String name) {
        return prefix + "_" + name;
    }

    /**
     * 真实索引名。只在建索引、重建、删索引时用，正常读写一律走别名。
     *
     * @param name    逻辑索引名，如 product
     * @param version 版本号
     * @return 真实索引名，如 dong_lab_product_v1
     */
    public String versioned(String name, int version) {
        return prefix + "_" + name + "_v" + version;
    }

    /**
     * 从真实索引名里反解析版本号，用来算下一个版本。解析不出来就当第 1 版。
     *
     * @param index 真实索引名
     * @return 版本号
     */
    public int versionOf(String index) {
        Matcher matcher = VERSION_PATTERN.matcher(index);
        if (!matcher.find()) {
            return 1;
        }
        return Integer.parseInt(matcher.group(1));
    }

}
