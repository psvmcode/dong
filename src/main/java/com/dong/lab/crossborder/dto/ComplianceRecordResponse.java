package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.ComplianceRecord;
import com.dong.lab.crossborder.enums.ComplianceCheckType;
import com.dong.lab.crossborder.enums.ComplianceResult;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合规检查记录响应。一笔汇款对应四条自动检查加最多一条人工复核，
 * 每条记录谁检查的、结论是什么、命中了什么，监管检查时按单号逐条出示。
 */
@Data
public class ComplianceRecordResponse {

    private String remittanceNo;

    private ComplianceCheckType checkType;

    private ComplianceResult result;

    private String hitDetail;

    private LocalDateTime createTime;

    public static ComplianceRecordResponse from(ComplianceRecord entity) {
        ComplianceRecordResponse response = new ComplianceRecordResponse();
        response.setRemittanceNo(entity.getRemittanceNo());
        response.setCheckType(entity.getCheckType());
        response.setResult(entity.getResult());
        response.setHitDetail(entity.getHitDetail());
        response.setCreateTime(entity.getCreateTime());
        return response;
    }

}
