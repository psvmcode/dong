package com.dong.tcc.service;

import com.dong.tcc.dto.TccOrderRequest;
import com.dong.tcc.dto.TccResultResponse;
import com.dong.tcc.entity.TccBranch;

import java.util.List;
import java.util.Map;

/**
 * 分布式事务协调者。三阶段依次是 Try 冻结资源、Confirm 确认扣减、Cancel 释放冻结。
 *
 * <p>三个必须处理的问题：
 * 幂等靠分支表唯一索引防重复提交；
 * 空回滚是 Try 未执行却收到 Cancel，此时直接返回成功；
 * 悬挂是 Cancel 先到、Try 后到，需要在 Try 前检查事务状态。
 */
public interface TccCoordinatorService {

    /**
     * 提交分布式订单，forceFailure 为 true 时强制走 Cancel 以验证回滚。
     */
    TccResultResponse submit(TccOrderRequest request);

    /**
     * 查询事务状态。
     */
    Map<String, Object> status(String xid);

    /**
     * 恢复停留在中间状态的事务，返回处理数量。
     */
    int recoverPending();

    /**
     * 查询事务的各分支记录。
     */
    List<TccBranch> branches(String xid);

    /**
     * 初始化演示用的库存与账户。
     */
    void seed(Long userId, Long productId, int available, long balance);

}
