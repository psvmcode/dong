package com.dong.replica.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.replica.entity.UserAccount;
import com.dong.replica.mapper.UserAccountMapper;
import com.dong.replica.service.UserAccountService;
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
@ConditionalOnProperty(prefix = "dong.mariadb", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class UserAccountServiceImpl implements UserAccountService {

    /**
     * userAccountMapper，MyBatis Mapper 数据访问层。
     */
    private final UserAccountMapper userAccountMapper;

    /**
     * 创建记录。
     */
    @Override
    @Transactional(transactionManager = "replicaTransactionManager", rollbackFor = Exception.class)
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

    /**
     * findByUserId。
     */
    @Override
    public UserAccount findByUserId(Long userId) {
        return userAccountMapper.selectByUserId(userId);
    }

    /**
     * 查询全部。
     */
    @Override
    public List<UserAccount> findAll() {
        return userAccountMapper.selectAll();
    }

    /**
     * transfer。
     */
    @Override
    @Transactional(transactionManager = "replicaTransactionManager", rollbackFor = Exception.class)
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

    /**
     * consistencyCheck。
     */
    @Override
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
