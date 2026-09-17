package com.dong.search.dto;

import lombok.Data;

import java.util.List;
/**
 * 深分页结果。
 *
 * <p>用 search_after 而不是 from+size：from+size 的实现是把每个分片的前 from+size 条都捞出来再排序截断，
 * 翻到第 1000 页时协调节点要处理的数据量是按页数线性增长的，ES 默认还卡着
 * index.max_result_window=10000 的硬上限，超过直接报错。
 *
 * <p>search_after 的原理是「记住上一页最后一条的排序值，下一页从这个值之后继续」，
 * 代价是必须有一个稳定且唯一的排序键（这里用价格加 id），否则游标会漂。
 * 另一个代价是不能跳页，只能一页一页往下翻。
 */
@Data

public class DeepSearchResponse {

    /**
     * 本页数据。
     */
    private List<ProductSearchResponse.Hit> list;

    /**
     * 下一页游标，即最后一条的排序值。原样塞回 after 参数即可。
     * 为 null 表示后面没有了。
     */
    private String nextAfter;

    /**
     * 是否还有下一页。
     */
    private boolean hasMore;

}
