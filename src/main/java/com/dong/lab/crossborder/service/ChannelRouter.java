package com.dong.lab.crossborder.service;

import com.dong.lab.crossborder.enums.SettlementChannel;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 智能渠道路由。真实跨境支付系统的核心能力之一：
 * 同一笔汇款往往有多个渠道可走，路由器按成本、时效与额度约束选出最优渠道。
 *
 * <p>选型的三个维度：
 * 成本按各渠道费率计算总费用；
 * 时效按渠道平均到账时间衡量，加急汇款应偏向快渠道；
 * 额度约束是硬规则，超过渠道单笔上限直接排除，再便宜也不能走。
 */
public interface ChannelRouter {

    /**
     * 为一笔汇款选出最优渠道。
     *
     * @param sourceAmount 源币种金额
     * @param urgent       是否加急，加急会放大时效权重
     * @return 选中的渠道与决策依据
     */
    RouteDecision route(BigDecimal sourceAmount, boolean urgent);

    /**
     * 全渠道评分明细，供前端展示与人工核对。
     */
    Map<String, Object> scoreAll(BigDecimal sourceAmount, boolean urgent);

    /**
     * 路由决策，包含选中渠道与各渠道的评分明细，便于排查为什么走了这个渠道。
     */
    record RouteDecision(SettlementChannel channel, BigDecimal estimatedFee,
                         java.util.List<String> reasons) {
    }

}
