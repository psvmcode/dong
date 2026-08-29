package com.dong.lab.doc.service;

import com.dong.lab.common.result.PageResult;
import com.dong.lab.doc.dto.OperationLogRequest;
import com.dong.lab.doc.entity.OperationLogDocument;

public interface OperationLogService {

    String save(OperationLogRequest request);

    PageResult<OperationLogDocument> findByPage(String bizType, int pageNum, int pageSize);

    long count();

}
