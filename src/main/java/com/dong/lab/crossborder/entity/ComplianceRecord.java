package com.dong.lab.crossborder.entity;

import com.dong.lab.crossborder.enums.ComplianceCheckType;
import com.dong.lab.crossborder.enums.ComplianceResult;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合规检查记录。每一道检查都留痕，这既是监管要求，
 * 也是事后审计和争议处理的依据。
 */
@Data
public class ComplianceRecord {

    private Long id;

    /** 关联的汇款单号 */
    private String remittanceNo;

    /** 检查类型：制裁名单、KYC、反洗钱、限额、人工复核 */
    private ComplianceCheckType checkType;

    /** 检查结论：通过、拒绝、转人工 */
    private ComplianceResult result;

    /** 命中详情，未命中为空串 */
    private String hitDetail;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
