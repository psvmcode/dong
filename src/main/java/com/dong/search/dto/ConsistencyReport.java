package com.dong.search.dto;

import lombok.Data;

import java.util.List;

/**
 * 索引一致性对账报告。
 *
 * <p>三个差异列表描述的都是「修复之前」的检测结果，配合 repairedCount
 * 才能看出这一轮到底发现了什么、又修掉了多少。修复之后想再确认，就重新对账一次。
 */
@Data
public class ConsistencyReport {

    /**
     * 数据库里的商品数。
     */
    private int dbCount;

    /**
     * 索引里的文档数。
     */
    private int esCount;

    /**
     * 数据库有、索引没有的商品 id，对应漏同步。
     */
    private List<String> missingIds = List.of();

    /**
     * 两边都有但关键字段不一致的 id，对应同步到一半或者被人直接改过索引。
     */
    private List<String> staleIds = List.of();

    /**
     * 索引有、数据库没有的文档 id，对应商品已删除但文档没删，搜索会返回幽灵商品。
     */
    private List<String> orphanIds = List.of();

    /**
     * 本次是否执行了修复。
     */
    private boolean repaired;

    /**
     * 本次实际修复的文档数，等于重写数加删除数。
     */
    private int repairedCount;

}
