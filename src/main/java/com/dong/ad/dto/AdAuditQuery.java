package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审计轨迹查询条件。三个过滤条件都可选，查不到返回空集。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdAuditQuery extends PageQuery {

    /**
     * 目标类型，如 advertiser / campaign / unit / creative。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String targetType;

    /**
     * 目标记录 id。
     */
    @Positive
    private Long targetId;

    /**
     * 动作名称，如 update_campaign / transition:START。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String action;

}
