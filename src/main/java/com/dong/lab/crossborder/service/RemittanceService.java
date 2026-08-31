package com.dong.lab.crossborder.service;

import com.dong.lab.common.result.PageResult;
import com.dong.lab.crossborder.dto.RemittanceCreateRequest;
import com.dong.lab.crossborder.dto.RemittanceResponse;
import com.dong.lab.crossborder.enums.RemittanceStatus;

import java.util.List;
import java.util.Map;

/**
 * 跨境汇款主流程。
 *
 * <p>采用本地事务加消息最终一致，而不是分布式事务：
 * 扣款与记账在同一个数据库事务内完成，保证资金不会凭空消失；
 * 清算与到账通过消息队列异步推进，用幂等消费和每日对账兜底。
 *
 * <p>这是多数支付系统的实际选择。分布式事务能保证强一致，
 * 但会把渠道方也拉进事务边界，而跨境渠道本身只支持异步批量清算，
 * 强一致在这里既做不到也没有必要。
 */
public interface RemittanceService {

    /**
     * 发起汇款。全链路依次为幂等校验、合规筛查、锁汇、扣款、发送清算消息。
     *
     * @return 汇款单，幂等重放时返回原单而不是报错
     */
    RemittanceResponse create(RemittanceCreateRequest request);

    RemittanceResponse findByRemittanceNo(String remittanceNo);

    RemittanceResponse findByIdempotentKey(String idempotentKey);

    PageResult<RemittanceResponse> findByPage(RemittanceStatus status, int pageNum, int pageSize);

    List<RemittanceResponse> findByBatchNo(String batchNo);

    /**
     * 推进到下一状态，供消息消费者与补偿任务调用。
     * 幂等：已处于目标状态时直接返回 true。
     */
    boolean advance(String remittanceNo, RemittanceStatus expected, RemittanceStatus target);

    /**
     * 标记为失败并退款。资金已扣的会原路退回。
     */
    void failAndRefund(String remittanceNo, String reason);

    /**
     * 运行时统计，便于观察各状态的单量与中间件使用情况。
     */
    Map<String, Object> runtime();

    int clearAll();

}
