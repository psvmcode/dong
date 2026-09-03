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

    /**
     * 关联的汇款单号，一笔汇款可能对应多条合规检查记录。
     */
    private String remittanceNo;

    /**
     * 检查类型，例如黑名单、限额、反洗钱等自动或人工检查。
     */
    private ComplianceCheckType checkType;

    /**
     * 检查结果，放行、拦截或需人工复核。
     */
    private ComplianceResult result;

    /**
     * 命中详情，记录命中规则、名单或阈值的明细，供审计使用。
     */
    private String hitDetail;

    /**
     * 检查发生时间。
     */
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
