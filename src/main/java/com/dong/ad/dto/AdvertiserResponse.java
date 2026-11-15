package com.dong.ad.dto;

import com.dong.ad.entity.AdAdvertiser;
import com.dong.ad.enums.AdSwitchStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广告主账户响应。
 */
@Data
public class AdvertiserResponse {

    /**
     * 主键
     */
    private Long id;

    /**
     * 广告主编号
     */
    private String advertiserNo;

    /**
     * 名称
     */
    private String name;

    /**
     * 可用余额，单位分
     */
    private Long balanceCents;

    /**
     * 状态名称
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 由实体组装响应。
     *
     * @param entity 广告主实体
     * @return 响应对象
     */
    public static AdvertiserResponse from(AdAdvertiser entity) {
        AdvertiserResponse response = new AdvertiserResponse();
        response.setId(entity.getId());
        response.setAdvertiserNo(entity.getAdvertiserNo());
        response.setName(entity.getName());
        response.setBalanceCents(entity.getBalanceCents());
        response.setStatus(AdSwitchStatus.of(entity.getStatus()).name());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

}
