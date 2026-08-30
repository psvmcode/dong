package com.dong.lab.search.service;

/**
 * 索引同步，把 MySQL 的全量商品重建到 Elasticsearch。
 */
public interface SearchSyncService {

    /**
     * 全量同步，返回写入的文档数。
     */
    int syncAll();

}
