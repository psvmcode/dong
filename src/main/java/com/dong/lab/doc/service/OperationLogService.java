package com.dong.lab.doc.service;

import com.dong.lab.common.result.PageResult;
import com.dong.lab.doc.dto.OperationLogRequest;
import com.dong.lab.doc.entity.OperationLogDocument;

/**
 * 操作日志。用 MongoDB 存储，因为日志字段会随业务不断演进，
 * 无 schema 特性避免了每次加字段都要改表结构。
 */
public interface OperationLogService {

    /**
     * 写入一条日志，detail 是任意对象，不同业务可以写入完全不同的结构。
     */
    String save(OperationLogRequest request);

    /**
     * 按业务类型分页查询。
     */
    PageResult<OperationLogDocument> findByPage(String bizType, int pageNum, int pageSize);

    /**
     * 查询总条数。
     */
    long count();

}
