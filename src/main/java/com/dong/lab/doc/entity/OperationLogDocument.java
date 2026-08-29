package com.dong.lab.doc.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Document(collection = "operation_log")
public class OperationLogDocument {

    @Id
    private String id;

    @Indexed
    private String bizType;

    @Indexed
    private String bizId;

    private String operator;

    private String action;

    private Map<String, Object> detail;

    @Indexed
    private LocalDateTime createTime;

}
