package com.dong.crossborder.mapper;

import com.dong.crossborder.entity.ChannelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 清算渠道配置数据访问。
 */
@Mapper

public interface ChannelConfigMapper {

    /**
     * 按渠道编码查配置。
     */
    ChannelConfig selectByChannel(@Param("channel") int channel);

    /**
     * 查询全部配置，管理接口使用。
     */
    List<ChannelConfig> selectAll();

    /**
     * 只查启用的渠道，路由打分时用——停用的渠道等于熔断。
     */
    List<ChannelConfig> selectEnabled();

    /**
     * 新增渠道配置。
     */
    int insert(ChannelConfig config);

    /**
     * 更新渠道配置。
     */
    int update(ChannelConfig config);

    /**
     * 启停渠道，渠道故障时的熔断开关。
     */
    int updateEnabled(@Param("channel") int channel, @Param("enabled") int enabled);

}
