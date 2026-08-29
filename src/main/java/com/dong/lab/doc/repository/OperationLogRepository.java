package com.dong.lab.doc.repository;

import com.dong.lab.doc.entity.OperationLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OperationLogRepository extends MongoRepository<OperationLogDocument, String> {

    List<OperationLogDocument> findByBizTypeAndBizId(String bizType, String bizId);

}
