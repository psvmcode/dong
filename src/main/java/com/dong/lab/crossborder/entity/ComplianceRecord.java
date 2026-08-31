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

    private String remittanceNo;

    private ComplianceCheckType checkType;

    private ComplianceResult result;

    private String hitDetail;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
