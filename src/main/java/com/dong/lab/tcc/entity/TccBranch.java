package com.dong.lab.tcc.entity;

import com.dong.lab.tcc.enums.TccBranchStatus;
import lombok.Data;

import java.time.LocalDateTime;
/**
 * TCC 分支事务。记录某个参与者在全局事务中的状态与参数快照，
 * 恢复任务根据 nextRetryTime 扫描并推进分支状态。
 */
@Data

public class TccBranch {

    /**
     * 主键
     */
    private Long id;

    /**
     * 全局事务 id
     */
    private String xid;

    /**
     * 分支 id，同一事务内唯一
     */
    private String branchId;

    /**
     * 分支状态
     */
    private TccBranchStatus status;

    /**
     * 业务参数快照，恢复任务重试时回放
     */
    private String payload;

    /**
     * 失败原因，有非空约束，无错时写空字符串
     */
    private String errorMessage;

    /**
     * 下次重试时间，恢复任务按此扫描
     */
    private LocalDateTime nextRetryTime;

    /**
     * 已重试次数
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
