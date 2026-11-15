package com.dong.ad.dto;

import com.dong.ad.entity.AdUnit;
import com.dong.ad.enums.AdBidType;
import com.dong.ad.enums.AdSwitchStatus;
import com.dong.common.util.JsonUtils;
import lombok.Data;

/**
 * 广告单元响应。
 */
@Data
public class AdUnitResponse {

    /**
     * 主键
     */
    private Long id;

    /**
     * 单元编号
     */
    private String unitNo;

    /**
     * 计划 id
     */
    private Long campaignId;

    /**
     * 名称
     */
    private String name;

    /**
     * 定向条件
     */
    private AdTargeting targeting;

    /**
     * 计费类型名称
     */
    private String bidType;

    /**
     * 出价，单位分
     */
    private Long bidCents;

    /**
     * 保留价，单位分
     */
    private Long floorCents;

    /**
     * 预估点击率，万分比
     */
    private Integer pctr;

    /**
     * 状态名称
     */
    private String status;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 由实体组装响应。定向 JSON 写脏时返回全不限，不让单个脏数据毁掉整个列表接口。
     *
     * @param entity 单元实体
     * @return 响应对象
     */
    public static AdUnitResponse from(AdUnit entity) {
        AdUnitResponse response = new AdUnitResponse();
        response.setId(entity.getId());
        response.setUnitNo(entity.getUnitNo());
        response.setCampaignId(entity.getCampaignId());
        response.setName(entity.getName());
        response.setTargeting(parseTargeting(entity.getTargeting()));
        response.setBidType(AdBidType.of(entity.getBidType()).name());
        response.setBidCents(entity.getBidCents());
        response.setFloorCents(entity.getFloorCents());
        response.setPctr(entity.getPctr());
        response.setStatus(AdSwitchStatus.of(entity.getStatus()).name());
        response.setVersion(entity.getVersion());
        return response;
    }

    /**
     * 解析定向 JSON。
     *
     * @param json 定向 JSON
     * @return 定向条件
     */
    private static AdTargeting parseTargeting(String json) {
        if (json == null || json.isBlank()) {
            return AdTargeting.any();
        }
        try {
            return JsonUtils.fromJson(json, AdTargeting.class).normalized();
        } catch (Exception ex) {
            return AdTargeting.any();
        }
    }

}
