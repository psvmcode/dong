package com.dong.lab.search.service;

import com.dong.lab.search.dto.ProductSearchRequest;
import com.dong.lab.search.dto.ProductSearchResponse;
import com.dong.lab.search.entity.ProductDocument;

/**
 * 商品搜索。索引映射由启动时显式创建，category 为 keyword 以支持聚合，
 * name 和 description 用 ik_max_word 索引、ik_smart 查询。
 *
 * <p>不能依赖动态映射：ES 会把字符串默认推断成 text，
 * 而对 text 字段做 terms 聚合是非法的，分面统计会直接报错。
 */
public interface SearchService {

    /**
     * 写入单个文档。
     */
    void index(ProductDocument document);

    /**
     * 批量写入。必须检查响应里的 errors 标志：
     * ES 的 bulk 接口即使单条失败也返回 200，
     * 否则日期格式不匹配这类问题会静默吞掉所有写入。
     */
    void bulkIndex(Iterable<ProductDocument> documents);

    /**
     * 按 id 删除文档。
     */
    void deleteById(String id);

    /**
     * 全文检索，支持过滤、高亮与分面聚合。
     */
    ProductSearchResponse search(ProductSearchRequest request);

    /**
     * 查询文档总数。
     */
    long count();

}
