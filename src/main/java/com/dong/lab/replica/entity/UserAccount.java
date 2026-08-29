package com.dong.lab.replica.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserAccount {

    private Long id;

    private Long userId;

    private String username;

    private Long balance;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
