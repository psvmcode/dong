package com.dong.lab.tcc.entity;

import com.dong.lab.tcc.enums.TccBranchStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccBranch {

    private Long id;

    private String xid;

    private String branchId;

    private TccBranchStatus status;

    private String payload;

    private String errorMessage;

    private LocalDateTime nextRetryTime;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
