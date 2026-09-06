package com.dong.crossborder.service;

import com.dong.crossborder.dto.ReconReportResponse;
import com.dong.crossborder.enums.ReconDiffType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 对账核销。
 *
 * <p>对账是支付系统每日必做的闭环动作：渠道清算后会下发回单文件，
 * 系统拿本地流水与渠道回单逐笔比对，对不上的记入差异表由运营处理。
 * 不做对账的资金系统是不完整的——漏入账、多扣款都会被掩盖。
 *
 * <p>五类差异各有处理方式：
 * 长款（渠道记的比本地多）：可能是渠道多放款，需要退款给渠道或挂账；
 * 短款（渠道记的比本地少）：可能是渠道漏放款，需要向渠道追讨；
 * 金额不一致：通常是汇率精度或手续费计算方式不同，需要逐笔核对；
 * 渠道有本地无：可能是本地系统故障丢了单子，需要补单；
 * 本地有渠道无：可能是渠道尚未完成清算，需要等下一轮或追查。
 */
public interface ReconciliationService {

    /**
     * 为一个批次执行对账。返回对账报告，差异自动写入差异表。
     *
     * <p>渠道回单数据由 generateChannelStatement 模拟，
     * 真实系统从渠道的 SFTP 或 API 拉取。
     *
     * @param batchNo             清算批次号，批次必须是 CLOSED 或 SETTLED 状态
     * @param simulatedErrorRate  模拟渠道差错率，0 到 1 之间。0 表示渠道回单完全准确，
     *                            大于 0 时会注入漏单、金额偏移与多余单，用来验证对账能否发现差异
     * @return 对账报告
     */
    ReconReportResponse reconcile(String batchNo, double simulatedErrorRate);

    /**
     * 模拟渠道回单。基于本地已结算的汇款单生成，
     * 按注入的差错率引入金额偏差、漏单和多余单，用来测试对账逻辑。
     */
    List<Map<String, Object>> generateChannelStatement(String batchNo, double errorRate);

    /**
     * 查询对账报告。
     */
    ReconReportResponse report(String batchNo);

    /**
     * 运营处理单笔差异。type 决定处理方式：
     * 长款挂账、短款追讨、金额不一致核销、缺失补单。
     */
    Map<String, Object> handleDiff(Long diffId, ReconDiffType diffType, String decision);

    /**
     * 批量处理某批次全部未处理差异。
     */
    int handleAllUnhandled(String batchNo, String decision);

    /**
     * 对账总览，含未处理差异数与按类型分布。
     */
    Map<String, Object> overview();

}
