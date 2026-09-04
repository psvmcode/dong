package com.dong.lab.doc.service.impl;

import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.result.PageResult;
import com.dong.lab.doc.dto.OperationLogRequest;
import com.dong.lab.doc.entity.OperationLogDocument;
import com.dong.lab.doc.repository.OperationLogRepository;
import com.dong.lab.doc.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 操作日志实现。基于 MongoDB，
 * 适合字段会随业务演进、结构不固定的日志数据。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lab.mongodb", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class OperationLogServiceImpl implements OperationLogService {

    /**
     * 操作日志数据仓库。
     */
    private final OperationLogRepository operationLogRepository;

    /**
     * MongoDB 操作模板。
     */
    private final MongoTemplate mongoTemplate;

    /**
     * 保存记录。
     */
    @Override
    public String save(OperationLogRequest request) {
        OperationLogDocument document = new OperationLogDocument();
        document.setBizType(request.getBizType());
        document.setBizId(request.getBizId());
        document.setOperator(request.getOperator());
        document.setAction(request.getAction());
        document.setDetail(request.getDetail());
        document.setCreateTime(LocalDateTime.now());
        OperationLogDocument saved = operationLogRepository.save(document);
        log.info("operation log saved bizType={} bizId={}", request.getBizType(), request.getBizId());
        return saved.getId();
    }

    /**
     * 分页查询。
     */
    @Override
    public PageResult<OperationLogDocument> findByPage(String bizType, int pageNum, int pageSize) {
        PageRequest request = PageRequest.of(pageNum, pageSize);
        Query query = new Query();
        if (bizType != null && !bizType.isBlank()) {
            query.addCriteria(Criteria.where("bizType").is(bizType));
        }

        long total = mongoTemplate.count(query, OperationLogDocument.class);
        List<OperationLogDocument> list = mongoTemplate.find(query.with(Pageable.ofSize(request.getPageSize())
                .withPage(request.getPageNum() - 1)), OperationLogDocument.class);
        return PageResult.of(list, total, request);
    }

    /**
     * 查询日志总条数。
     *
     * @return 日志总条数
     */
    @Override
    public long count() {
        return operationLogRepository.count();
    }

}
