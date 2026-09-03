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
    /**
     * 文档 id，MongoDB 自动生成
     */
    private String id;

    @Indexed
    /**
     * 业务类型，如 order、seckill
     */
    private String bizType;

    @Indexed
    /**
     * 业务单号
     */
    private String bizId;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 操作动作
     */
    private String action;

    private Map<String, Object> detail;

    @Indexed
    /**
     * 操作时间
     */
    private LocalDateTime createTime;

}
