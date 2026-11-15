package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 广告模块通用分页查询条件。
 *
 * <p>target 决定查哪张表：advertiser / campaign / unit / creative / event。
 * parentId 按父级过滤（计划按广告主、单元与创意按计划、事件按链路），
 * 所有条件都是可选的，查不到返回空集而不是报错。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdQuery extends PageQuery {

    /**
     * 查询目标类型。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String target;

    /**
     * 父级过滤 id，可选。
     */
    @Positive
    private Long parentId;

    /**
     * 状态过滤，可选。越界值由枚举 of 兜成 1000。
     */
    @Min(0)
    @Max(10)
    private Integer status;

    /**
     * 名称或编号关键字，可选。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String keyword;

    /**
     * 投放链路 id，仅事件查询使用，可选。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String traceId;

}
