package com.dong.lab.doc.repository;

import com.dong.lab.doc.entity.OperationLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * 操作日志 MongoDB 数据访问接口。
 */
public interface OperationLogRepository extends MongoRepository<OperationLogDocument, String> {

    /**
     * 按业务类型与业务单号查询操作日志。
     *
     * @param bizType 业务类型
     * @param bizId   业务单号
     * @return 操作日志列表
     */
    List<OperationLogDocument> findByBizTypeAndBizId(String bizType, String bizId);

}
