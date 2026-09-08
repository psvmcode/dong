package com.dong.crossborder.service;

import com.dong.crossborder.entity.ChannelConfig;

import java.math.BigDecimal;
import java.util.List;
/**
 * 清算渠道配置。
 *
 * <p>渠道的时效、单笔上限与费率都会变：代理行调价、监管改额度、
 * 某条线路临时故障。落库之后这些调整不必发版，
 * 停用渠道还能在故障期间把流量切走，是最直接的熔断手段。
 */
public interface ChannelConfigService {

    /**
     * 查询全部渠道配置，含停用的，便于运营看到完整状态。
     *
     * @return 渠道配置列表
     */
    List<ChannelConfig> all();

    /**
     * 只查启用渠道，路由打分时使用。
     *
     * @return 可用渠道配置列表
     */
    List<ChannelConfig> enabled();

    /**
     * 按渠道编码查配置。
     *
     * @param channel 渠道编码
     * @return 渠道配置
     */
    ChannelConfig byChannel(int channel);

    /**
     * 更新渠道参数。
     *
     * @param channel    渠道编码
     * @param etaMinutes 预计到账分钟数
     * @param perTxLimit 单笔上限
     * @param fixedFee   固定手续费
     * @param rateFee    比例手续费
     * @param enabled    是否启用
     */
    void update(int channel, Long etaMinutes, BigDecimal perTxLimit, BigDecimal fixedFee,
                BigDecimal rateFee, Integer enabled);

    /**
     * 启停渠道。渠道故障时停用，等于把它从路由候选里摘掉。
     *
     * @param channel 渠道编码
     * @param enabled 是否启用
     */
    void setEnabled(int channel, boolean enabled);

}
