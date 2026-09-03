package com.dong.lab.crossborder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工审核决策请求。reviewer 必填：合规决策必须能追溯到具体的人，
 * 匿名的放行或驳回在监管检查时等同于没有做过审核。
 */
@Data
public class ReviewDecisionRequest {

    @NotBlank
    @Size(max = 64)
    private String reviewer;

    @Size(max = 255)
    private String note;

}
