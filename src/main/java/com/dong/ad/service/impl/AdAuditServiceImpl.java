package com.dong.ad.service.impl;

import com.dong.ad.entity.AdAuditLog;
import com.dong.ad.mapper.AdAuditLogMapper;
import com.dong.ad.service.AdAuditService;
import com.dong.common.constant.Constants;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 配置变更审计实现。
 *
 * <p>审计是旁路能力：写失败只记错误日志，绝不抛出。
 * 如果因为审计表抖动就让预算调整失败，主流程反而被旁路拖垮，本末倒置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdAuditServiceImpl implements AdAuditService {

    /**
     * adAuditLogMapper，MyBatis Mapper 数据访问层。
     */
    private final AdAuditLogMapper adAuditLogMapper;

    /**
     * 审计开关。
     */
    @Value("${dong.ad.audit-enabled:true}")
    private boolean enabled;

    /**
     * 写一条审计记录。
     */
    @Override
    public void write(String operator, String action, String targetType, Long targetId, String beforeJson, String afterJson, String reason) {
        if (!enabled) {
            return;
        }
        try {
            AdAuditLog logRecord = new AdAuditLog();
            logRecord.setOperator(operator == null || operator.isBlank() ? "system" : trim(operator, 64));
            logRecord.setAction(trim(action, 64));
            logRecord.setTargetType(trim(targetType, 32));
            logRecord.setTargetId(targetId);
            logRecord.setBeforeJson(trim(beforeJson, Constants.MAX_AUDIT_SNAPSHOT));
            logRecord.setAfterJson(trim(afterJson, Constants.MAX_AUDIT_SNAPSHOT));
            logRecord.setReason(trim(reason, 512));
            adAuditLogMapper.insert(logRecord);
        } catch (Exception ex) {
            // 审计失败只记日志：不能让旁路把预算调整、状态迁移这些主流程拖垮
            log.error("ad audit write failed action={} target={}/{}", action, targetType, targetId, ex);
        }
    }

    /**
     * 分页查询审计轨迹。
     */
    @Override
    public PageResult<AdAuditLog> query(String targetType, Long targetId, String action, int pageNum, int pageSize) {
        PageRequest page = PageRequest.of(pageNum, pageSize);
        List<AdAuditLog> list = adAuditLogMapper.selectByPage(targetType, targetId, action, page.getOffset(), page.getPageSize());
        long total = adAuditLogMapper.countByPage(targetType, targetId, action);
        return PageResult.of(list, total, page);
    }

    /**
     * 截断超长字符串，避免脏数据把审计表字段撑爆。
     *
     * @param value 原值
     * @param max   最大长度
     * @return 安全字符串
     */
    private String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

}
