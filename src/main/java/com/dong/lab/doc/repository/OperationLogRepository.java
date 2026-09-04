package com.dong.lab.doc.repository;

import com.dong.lab.doc.entity.OperationLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * OperationLogRepository。
 */
public interface OperationLogRepository extends MongoRepository<OperationLogDocument, String> {

    /**
     * findByBizTypeAndBizId。
     */
    List<OperationLogDocument> findByBizTypeAndBizId(String bizType, String bizId);

}
