package com.dong.agent.dto;

import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 实验结果查询请求。实验编号留空则查全部。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LabResultQuery extends PageQuery {

    /**
     * 实验编号，留空查全部。
     */
    @Size(max = 32)
    private String experiment;

}
