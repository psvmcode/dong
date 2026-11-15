package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 广告模块通用动作请求。
 *
 * <p>详情、状态迁移、创意审核、单元开关、账户充值这些动作都只改一个 id，
 * 为每件事建一个 DTO 只会增加重复代码，所以共用一个请求体并按字段取值：
 * 用得到的字段才校验，用不到的字段留空即可。
 */
@Data
public class AdActionRequest {

    /**
     * 目标记录 id。
     */
    @NotNull
    @Positive
    private Long id;

    /**
     * 生命周期事件名称，仅状态迁移接口使用。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String event;

    /**
     * 审核备注或驳回原因，可选。
     */
    @Size(max = Constants.MAX_TEXT_LENGTH)
    private String remark;

    /**
     * 审核结论，仅创意审核使用：approve 或 reject。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String decision;

    /**
     * 充值金额，单位分，仅账户充值使用。
     */
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long amountCents;

    /**
     * 期望的开关状态，仅单元开关使用：0 暂停 1 启用。
     */
    @Min(0)
    @Max(1)
    private Integer enabled;

}
