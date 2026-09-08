package com.dong.crossborder.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.crossborder.entity.ChannelConfig;
import com.dong.crossborder.mapper.ChannelConfigMapper;
import com.dong.crossborder.service.ChannelConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
/**
 * 渠道配置实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ChannelConfigServiceImpl implements ChannelConfigService {

    /**
     * channelConfigMapper。
     */
    private final ChannelConfigMapper channelConfigMapper;

    /**
     * 查询全部渠道配置。
     */
    @Override
    public List<ChannelConfig> all() {
        return channelConfigMapper.selectAll();
    }

    /**
     * 只查启用渠道。
     */
    @Override
    public List<ChannelConfig> enabled() {
        return channelConfigMapper.selectEnabled();
    }

    /**
     * 按编码查配置，不存在直接报错：没有配置的渠道不能参与路由。
     */
    @Override
    public ChannelConfig byChannel(int channel) {
        ChannelConfig config = channelConfigMapper.selectByChannel(channel);
        if (config == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "channel " + channel + " not configured");
        }
        return config;
    }

    /**
     * 更新渠道参数。
     */
    @Override
    public void update(int channel, Long etaMinutes, BigDecimal perTxLimit, BigDecimal fixedFee,
                       BigDecimal rateFee, Integer enabled) {
        byChannel(channel);
        ChannelConfig config = new ChannelConfig();
        config.setChannel(channel);
        config.setEtaMinutes(etaMinutes);
        config.setPerTxLimit(perTxLimit);
        config.setFixedFee(fixedFee);
        config.setRateFee(rateFee);
        config.setEnabled(enabled);
        channelConfigMapper.update(config);
        log.info("channel config updated channel={}", channel);
    }

    /**
     * 启停渠道。这是渠道故障期间最直接的熔断手段。
     */
    @Override
    public void setEnabled(int channel, boolean enabled) {
        byChannel(channel);
        channelConfigMapper.updateEnabled(channel, enabled ? 1 : 2);
        log.warn("channel toggled channel={} enabled={}", channel, enabled);
    }

}
