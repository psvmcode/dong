package com.dong.lab.crossborder.service;

import java.util.List;
import java.util.Map;

/**
 * 汇率敞口监控。
 *
 * <p>银行给客户锁汇的那一刻起就接下了汇率波动的风险：
 * 客户按锁定汇率成交，清算按市价交割，中间的差价是银行的盈亏。
 * 敞口监控回答两个问题：每个货币对压了多少未清算的锁定量，
 * 按当前市价浮盈还是浮亏。超过限额就要在市场上平盘对冲，
 * 这是真实外汇做市业务每天都要做的事。
 */
public interface FxExposureService {

    /**
     * 各货币对的敞口明细：未清算笔数、锁定量、当前市价估值与浮动盈亏。
     */
    List<Map<String, Object>> exposureByPair();

    /**
     * 敞口总量摘要，便于快速判断是否接近风控阈值。
     */
    Map<String, Object> summary();

}
