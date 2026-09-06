package com.dong.doc.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;
/**
 * 操作日志文档。以 MongoDB 文档形式记录关键业务操作，
 * 用于审计追溯、运营分析与故障排查。
 */
@Data
@Document(collection = "operation_log")

public class OperationLogDocument {

    /**
     * 文档 id，MongoDB 自动生成。
     */
    @Id
    private String id;

    /**
     * 业务类型，如 order、seckill，用于按业务维度检索日志。
     */
    @Indexed
    private String bizType;

    /**
     * 业务单号，与业务类型联合定位到具体业务记录。
     */
    @Indexed
    private String bizId;

    /**
     * 操作人。
     */
    private String operator;

    /**
     * 操作动作，例如创建、更新、删除。
     */
    private String action;

    /**
     * 操作详情，以 Map 记录变更前后的快照或扩展信息。
     */
    private Map<String, Object> detail;

    /**
     * 操作时间，按时间范围检索日志的主字段。
     */
    @Indexed
    private LocalDateTime createTime;

}
