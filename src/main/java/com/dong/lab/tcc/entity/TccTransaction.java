package com.dong.lab.tcc.entity;

import com.dong.lab.tcc.enums.TccTransactionStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccTransaction {

    private Long id;

    private String xid;

    private TccTransactionStatus status;

    private LocalDateTime expireTime;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
