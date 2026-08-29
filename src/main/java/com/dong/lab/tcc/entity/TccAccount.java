package com.dong.lab.tcc.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TccAccount {

    private Long id;

    private Long userId;

    private Long balance;

    private Long frozen;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
