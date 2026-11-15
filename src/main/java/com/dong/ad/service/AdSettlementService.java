package com.dong.ad.service;

import com.dong.ad.dto.AdChargeCommand;
import com.dong.ad.dto.DailyReportQuery;
import com.dong.ad.dto.DailyReportResponse;
import com.dong.ad.dto.ReconcileResponse;
import com.dong.common.result.PageResult;

import java.time.LocalDate;

/**
 * 计费结算与对账。
 *
 * <p>计费流水是金额与效果数据的唯一权威源：实时路径只是往里追加，
 * 日汇总、计划累计花费与 Redis 配额都由定时任务从流水重算。
 * 这样消息重复、丢失、乱序到达都只会造成「暂时不一致」，不会造成「金额错误」。
 */
public interface AdSettlementService {

    /**
     * 处理一条计费命令，返回 false 表示消息重复已被幂等拦截。
     *
     * @param command 计费命令
     * @return 是否处理了这条命令
     */
    boolean charge(AdChargeCommand command);

    /**
     * 以流水为权威源重算某天的日汇总，并重建 Redis 配额副本、补投漏单。
     * 重跑同一天结果一致；流水超过安全容量时抛 1007 而不是截断出错误结论。
     *
     * @param statDate      对账日期
     * @param resendPending 是否补投超时待计费事件
     * @return 对账结果
     */
    ReconcileResponse reconcile(LocalDate statDate, boolean resendPending);

    /**
     * 分页查询投放日报表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<DailyReportResponse> dailyReport(DailyReportQuery query);

    /**
     * 查询计划今日已计费金额，单位分。
     *
     * @param campaignId 计划 id
     * @return 今日已计费金额
     */
    long todayCharge(Long campaignId);

}
