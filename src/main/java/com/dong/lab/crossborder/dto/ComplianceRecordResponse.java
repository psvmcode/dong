package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.entity.ComplianceRecord;
import com.dong.lab.crossborder.enums.ComplianceCheckType;
import com.dong.lab.crossborder.enums.ComplianceResult;
import lombok.Data;

import java.time.LocalDateTime;

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
