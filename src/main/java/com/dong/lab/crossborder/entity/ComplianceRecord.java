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

    /**
     * 主键
     */
    private Long id;

    /**
     * 汇款单号
     */
    private String remittanceNo;

    /**
     * 检查类型，1 制裁名单 2 KYC 3 反洗钱 4 限额
     */
    private ComplianceCheckType checkType;

    /**
     * 检查结论，1 通过 2 拒绝 3 转人工审核
     */
    private ComplianceResult result;

    /**
     * 命中详情，未命中时为空字符串
     */
    private String hitDetail;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
