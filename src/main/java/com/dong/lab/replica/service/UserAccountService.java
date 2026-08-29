package com.dong.lab.replica.service;

import com.dong.lab.replica.entity.UserAccount;

import java.util.List;

public interface UserAccountService {

    Long create(Long userId, String username, long balance);

    UserAccount findByUserId(Long userId);

    List<UserAccount> findAll();

    long transfer(Long fromUserId, Long toUserId, long amount);

    java.util.Map<String, Object> consistencyCheck(Long userId);

}
