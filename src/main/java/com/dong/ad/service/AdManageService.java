package com.dong.ad.service;

import com.dong.ad.dto.AdActionRequest;
import com.dong.ad.dto.AdCreativeCreateRequest;
import com.dong.ad.dto.AdQuery;
import com.dong.ad.dto.AdUnitCreateRequest;
import com.dong.ad.dto.AdUnitUpdateRequest;
import com.dong.ad.dto.AdvertiserCreateRequest;
import com.dong.ad.dto.CampaignCreateRequest;
import com.dong.ad.dto.CampaignResponse;
import com.dong.ad.dto.CampaignUpdateRequest;
import com.dong.common.result.PageResult;

import java.util.Map;

/**
 * 广告投放配置管理：广告主、推广计划、广告单元与创意。
 *
 * <p>这些都是低频写、高频读的配置数据，因此不引入缓存：
 * 计划预算与状态一旦被缓存，暂停投放后缓存未失效就会继续扣钱，
 * 这类一致性代价远比省下的一次查询昂贵。
 */
public interface AdManageService {

    /**
     * 开立广告主账户。
     *
     * @param request 开户请求
     * @return 账户 id
     */
    Long createAdvertiser(AdvertiserCreateRequest request);

    /**
     * 账户充值。
     *
     * @param request 动作请求，取 amountCents
     * @return 充值后余额，单位分
     */
    Long recharge(AdActionRequest request);

    /**
     * 调整广告主账户日限额。0 表示不限，非零时同步刷新当天快照。
     *
     * @param id              广告主 id
     * @param dailyLimitCents 新的日限额，单位分
     */
    void updateAdvertiserDailyLimit(Long id, long dailyLimitCents);

    /**
     * 创建推广计划。
     *
     * @param request 创建请求
     * @return 计划 id
     */
    Long createCampaign(CampaignCreateRequest request);

    /**
     * 调整计划预算、结束日期与投放时段。
     *
     * @param request 调整请求
     */
    void updateCampaign(CampaignUpdateRequest request);

    /**
     * 查询计划详情，含今日已花费。
     *
     * @param id 计划 id
     * @return 计划详情
     */
    CampaignResponse campaign(Long id);

    /**
     * 推进计划生命周期状态。
     *
     * @param request 动作请求，取 event 与 remark
     * @return 推进后的计划
     */
    CampaignResponse transition(AdActionRequest request);

    /**
     * 创建广告单元。
     *
     * @param request 创建请求
     * @return 单元 id
     */
    Long createUnit(AdUnitCreateRequest request);

    /**
     * 调整单元出价与定向。
     *
     * @param request 调整请求
     */
    void updateUnit(AdUnitUpdateRequest request);

    /**
     * 启用或暂停单元。
     *
     * @param request 动作请求，取 enabled
     */
    void toggleUnit(AdActionRequest request);

    /**
     * 创建广告创意，初始为待审核。
     *
     * @param request 创建请求
     * @return 创意 id
     */
    Long createCreative(AdCreativeCreateRequest request);

    /**
     * 审核创意。
     *
     * @param request 动作请求，取 decision 与 remark
     */
    void reviewCreative(AdActionRequest request);

    /**
     * 按 target 分页查询配置或事件。
     *
     * @param query 通用查询条件
     * @return 分页结果
     */
    PageResult<Object> query(AdQuery query);

    /**
     * 查看模块运行时状态。
     *
     * @return 运行时状态
     */
    Map<String, Object> runtime();

}
