package com.dong.crossborder.service;

import com.dong.crossborder.dto.SettlementBatchResponse;
import com.dong.crossborder.enums.SettlementChannel;

import java.util.List;

/**
 * 清算服务。跨境清算按批次走，因为渠道有固定的清算窗口和起息时间，
 * 逐笔实时清算既不符合渠道规则也不经济。
 */
public interface SettlementService {

    /**
     * 创建清算批次。
     */
    String createBatch(SettlementChannel channel, String currency, long cutoffMinutes);

    /**
     * findByBatchNo。
     */
    SettlementBatchResponse findByBatchNo(String batchNo);

    /**
     * 查询全部。
     */
    List<SettlementBatchResponse> findAll();

    /**
     * 把已扣款的汇款单并入批次。
     */
    int collect(String batchNo, int limit);

    /**
     * 关闭到期批次，由定时任务调用。
     */
    int closeOverdue();

    /**
     * 执行清算：收款方入账并推进状态。
     *
     * @return 已清算的笔数
     */
    int settle(String batchNo);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
