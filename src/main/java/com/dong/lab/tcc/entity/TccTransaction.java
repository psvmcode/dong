package com.dong.lab.tcc.entity;

import com.dong.lab.tcc.enums.TccTransactionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccTransaction {

    /**
     * 主键
     */
    private Long id;

    /**
     * 全局事务 id
     */
    private String xid;

    /**
     * 事务状态，单向推进
     */
    private TccTransactionStatus status;

    /**
     * 事务过期时间，超时由恢复任务介入
     */
    private LocalDateTime expireTime;

    /**
     * 恢复重试次数
     */
    private Integer retryCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
