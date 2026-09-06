package com.dong.tcc.service;

import java.util.Map;

/**
 * TCC 参与者。每个参与者负责一种资源的冻结、确认与释放。
 *
 * <p>实现时三个方法都必须幂等，因为网络重试会导致重复调用。
 * cancelPhase 还要处理空回滚：Try 没执行却收到 Cancel，直接返回即可。
 */
public interface TccParticipant {

    /**
     * 分支标识，同一事务内必须唯一。
     */
    String branchId();

    /**
     * 冻结资源。需要检查事务状态以防悬挂：Cancel 已先到时不应再冻结。
     */
    void tryPhase(String xid, Map<String, Object> payload);

    /**
     * 确认扣减，把冻结的资源真正扣掉。
     */
    void confirmPhase(String xid, Map<String, Object> payload);

    /**
     * 释放冻结。要能处理空回滚，即 Try 从未执行的情况。
     */
    void cancelPhase(String xid, Map<String, Object> payload);

}
