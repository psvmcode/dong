package com.dong.search.service;

import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.entity.ProductDocument;

import java.util.Map;

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
     * 拉取索引里的文档，用于一致性对账。返回 id 到文档的映射，
     * 因为对账要按 id 与数据库逐条比对，用列表还得再建一次索引。
     *
     * @param limit 最多返回的条数
     * @return 文档 id 到文档的映射
     */
    Map<String, ProductDocument> listAll(int limit);

    /**
     * 批量删除文档。用于清理数据库里已经不存在的孤儿文档：
     * 逐条删会打出成百上千次请求，而对账修复时这个量级完全可能出现。
     *
     * @param ids 文档 id 集合
     */
    void bulkDelete(Iterable<String> ids);

    /**
     * 强制刷新索引，让刚写入的文档立刻能被搜到。
     *
     * <p>只给运维类动作收尾（全量重建、对账修复），业务写入路径不要调：
     * ES 本来就是近实时，默认最多 1 秒才可见，每次写都刷新会不停产生新分段，代价很高。
     * 但运维动作之后立刻复查是常规操作，不刷新就会出现「修复成功」和「复查仍报差异」同时发生。
     */
    void refresh();

    /**
     * 全文检索，支持过滤、高亮与分面聚合。
     */
    ProductSearchResponse search(ProductSearchRequest request);

    /**
     * 查询文档总数。
     */
    long count();

}
