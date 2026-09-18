package com.dong.search.service;

import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.dto.DeepSearchResponse;
import com.dong.search.dto.NearbySearchResponse;
import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.dto.SearchAggregateResponse;
import com.dong.search.entity.ProductDocument;

import java.util.List;
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
     * 删掉不在指定 id 名单里的文档，也就是清理孤儿。
     *
     * <p>走 delete_by_query 而不是「把索引里的 id 全捞回来再逐条删」：
     * 后者要求先把全量 id 装进内存，索引一大就先被内存卡住，
     * 结果是越堆积越清理不动——孤儿文档恰恰是最容易把索引撑大的时候。
     *
     * @param keepIds 要保留的文档 id，也就是数据库里还存在的商品
     * @return 删掉的文档数
     */
    long deleteExcept(Iterable<String> keepIds);

    /**
     * 强制刷新索引，让刚写入的文档立刻能被搜到。
     *
     * <p>只给运维类动作收尾（全量重建、对账修复），业务写入路径不要调：
     * ES 本来就是近实时，默认最多 1 秒才可见，每次写都刷新会不停产生新分段，代价很高。
     * 但运维动作之后立刻复查是常规操作，不刷新就会出现「修复成功」和「复查仍报差异」同时发生。
     */
    void refresh();

    /**
     * 全文检索，支持关键字、分类过滤、价格区间、排序、多字段高亮与分面聚合。
     */
    ProductSearchResponse search(ProductSearchRequest request);

    /**
     * 深分页检索，用 search_after 而不是 from+size。
     *
     * <p>from+size 翻到第 N 页要每个分片都捞前 from+size 条再归并截断，
     * 页数越深代价越大，ES 还卡着 max_result_window 的硬上限；
     * search_after 只认「上一页最后一条之后」，代价是不能跳页。
     *
     * @param sort  排序方式，见 ProductSearchRequest 里的排序常量
     * @param after 上一页返回的游标，第一页传 null
     * @param size  每页条数
     * @return 本页数据与下一页游标
     */
    DeepSearchResponse searchAfter(String sort, String after, int size);

    /**
     * 聚合统计。分面、数值统计、区间分布、时间直方图一次查完，
     * 过滤条件与检索保持一致，统计的才是用户看到的那一批数据。
     *
     * @param request 过滤条件
     * @return 聚合结果
     */
    SearchAggregateResponse aggregate(ProductSearchRequest request);

    /**
     * 前缀补全。走 completion suggester，不查倒排而是查内存里的 FST，
     * 所以快，代价是必须额外存一份 suggest 字段，且只能前缀匹配。
     *
     * @param prefix 用户输入的前缀
     * @param size   最多返回几条
     * @return 补全建议
     */
    List<String> suggest(String prefix, int size);

    /**
     * 附近商品。按与给定坐标的距离过滤并排序。
     *
     * <p>没填经纬度的商品不会有 location 字段，ES 会把它们直接排除在距离查询之外。
     *
     * @param latitude  纬度
     * @param longitude 经度
     * @param radiusKm  半径，单位公里
     * @param size      最多返回几条
     * @return 由近到远的商品
     */
    NearbySearchResponse nearby(double latitude, double longitude, double radiusKm, int size);

    /**
     * 查询文档总数。
     */
    long count();

}
