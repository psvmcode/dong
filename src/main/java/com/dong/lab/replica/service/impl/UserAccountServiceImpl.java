package com.dong.lab.replica.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.replica.entity.UserAccount;
import com.dong.lab.replica.mapper.UserAccountMapper;
import com.dong.lab.replica.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/**
 * 第二数据源账户实现。数据源与事务管理器都与主库独立，
 * 用于演示多数据源配置与本地事务边界。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lab.mariadb", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class UserAccountServiceImpl implements UserAccountService {

    /**
     * userAccountMapper，MyBatis Mapper 数据访问层。
     */
    private final UserAccountMapper userAccountMapper;

    @Override
    @Transactional(transactionManager = "replicaTransactionManager", rollbackFor = Exception.class)
    /**
     * 创建记录。
     */
    public Long create(Long userId, String username, long balance) {
        UserAccount existing = userAccountMapper.selectByUserId(userId);
        if (existing != null) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "account " + userId + " already exists on the replica instance");
        }

        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setUsername(username);
        account.setBalance(balance);
        userAccountMapper.insert(account);
        log.info("account created on replica instance userId={} balance={}", userId, balance);
        return account.getId();
    }

    @Override
    /**
     * findByUserId。
     */
    public UserAccount findByUserId(Long userId) {
        return userAccountMapper.selectByUserId(userId);
    }

    @Override
    /**
     * 查询全部。
     */
    public List<UserAccount> findAll() {
        return userAccountMapper.selectAll();
    }

    @Override
    @Transactional(transactionManager = "replicaTransactionManager", rollbackFor = Exception.class)
    /**
     * transfer。
     */
    public long transfer(Long fromUserId, Long toUserId, long amount) {
        UserAccount from = requireAccount(fromUserId);
        UserAccount to = requireAccount(toUserId);
        if (from.getBalance() < amount) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "balance not enough");
        }

        userAccountMapper.updateBalance(fromUserId, from.getBalance() - amount);
        userAccountMapper.updateBalance(toUserId, to.getBalance() + amount);
        log.info("transfer on replica instance from={} to={} amount={}", fromUserId, toUserId, amount);
        return amount;
    }

    @Override
    /**
     * consistencyCheck。
     */
    public Map<String, Object> consistencyCheck(Long userId) {
        UserAccount first = userAccountMapper.selectByUserId(userId);
        UserAccount second = userAccountMapper.selectByUserId(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("firstRead", first == null ? null : first.getBalance());
        result.put("secondRead", second == null ? null : second.getBalance());
        result.put("consistent", first != null && second != null && first.getBalance().equals(second.getBalance()));
        return result;
    }

    /**
     * requireAccount。
     */
    private UserAccount requireAccount(Long userId) {
        UserAccount account = userAccountMapper.selectByUserId(userId);
        if (account == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "account " + userId + " not found");
        }
        return account;
    }

}
