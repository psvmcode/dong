package com.dong.search.service;

import com.dong.search.dto.ConsistencyReport;

/**
 * 索引同步，把 MySQL 里的商品维护到 Elasticsearch。
 *
 * <p>MySQL 是唯一权威源，索引是可以随时丢弃重建的派生视图，
 * 所以同步只有一个方向：以库为准覆盖索引，不存在反向回写。
 */
public interface SearchSyncService {

    /**
     * 全量同步，返回写入的文档数。
     *
     * <p>顺带清理数据库里已经不存在的孤儿文档：只写不删的话，
     * 删掉商品之后索引里会一直留着幽灵文档，搜索结果里还能看到已经不存在的商品。
     */
    int syncAll();

    /**
     * 按 id 同步单个商品：回查数据库，查得到就覆盖索引，查不到就当删除处理。
     *
     * <p>只传 id 不传实体，保证写进索引的一定是数据库里的最新值，而且天然幂等。
     *
     * @param productId 商品 id
     */
    void syncOne(Long productId);

    /**
     * 一致性对账，只报告不修改。
     *
     * @return 对账报告
     */
    ConsistencyReport checkConsistency();

    /**
     * 一致性对账并修复：补写缺失与内容过期的文档，删除孤儿文档。
     *
     * @return 对账报告，repaired 与 repairedCount 表示本次修复动作
     */
    ConsistencyReport repairConsistency();

}
