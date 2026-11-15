package com.dong.ad.service;

import com.dong.ad.dto.AdEventRequest;
import com.dong.ad.dto.AdEventResponse;
import com.dong.ad.dto.AdQuery;
import com.dong.ad.dto.DeliveryRequest;
import com.dong.ad.dto.DeliveryResponse;
import com.dong.ad.enums.AdChargeMode;
import com.dong.ad.enums.AdEventType;
import com.dong.common.result.PageResult;

import java.util.Map;

/**
 * 投放链路：召回、定向、竞价、令牌签发、曝光与点击上报。
 *
 * <p>服务端在召回时把成交价签进令牌，上报事件绝不接收客户端传来的价格，
 * 这是广告计费最基本的一条信任边界。
 */
public interface AdDeliveryService {

    /**
     * 处理一次广告位请求，返回胜出广告与链路 id。
     *
     * @param request 广告请求
     * @return 投放响应
     */
    DeliveryResponse request(DeliveryRequest request);

    /**
     * 上报曝光或点击事件，按当前配置选择预算闸门与计费路径。
     *
     * @param request 事件请求
     * @param type    事件类型
     * @return 事件处置结果
     */
    AdEventResponse track(AdEventRequest request, AdEventType type);

    /**
     * 上报事件并显式指定预算闸门路径与是否同步计费，供对照实验使用。
     *
     * <p>实验要精确回读数据库金额，必须同步计费，否则异步消费会变成测量噪声。
     *
     * @param request    事件请求
     * @param type       事件类型
     * @param mode       预算闸门路径，null 表示按配置
     * @param syncCharge 是否强制同步计费
     * @return 事件处置结果
     */
    AdEventResponse track(AdEventRequest request, AdEventType type, AdChargeMode mode, boolean syncCharge);

    /**
     * 为并发实验直接签发一条指定计划的令牌，跳过召回过滤。
     *
     * @param campaignId 计划 id
     * @param userId     用户 id
     * @return 链路 id
     */
    String issueTokenForLab(Long campaignId, Long userId);

    /**
     * 分页查询投放事件。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<Object> events(AdQuery query);

    /**
     * 查看投放链路运行时状态。
     *
     * @return 运行时状态
     */
    Map<String, Object> runtime();

}
