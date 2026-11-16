package com.dong.ad.service;

import com.dong.ad.entity.AdAuditLog;
import com.dong.common.result.PageResult;

/**
 * 配置变更审计。预算与状态都是直接关联钱的操作，
 * 只留当前值无法回答「谁把日预算改大了一百倍」。
 */
public interface AdAuditService {

    /**
     * 写一条审计记录。审计失败由实现内部吞掉并记录日志，绝不影响主流程。
     *
     * @param operator   操作人
     * @param action     动作名称
     * @param targetType 目标类型
     * @param targetId   目标 id
     * @param beforeJson 变更前快照
     * @param afterJson  变更后快照
     * @param reason     原因
     */
    void write(String operator, String action, String targetType, Long targetId, String beforeJson, String afterJson, String reason);

    /**
     * 分页查询审计轨迹。
     *
     * @param targetType 目标类型过滤
     * @param targetId   目标 id 过滤
     * @param action     动作过滤
     * @param pageNum    页码
     * @param pageSize   每页大小
     * @return 分页结果
     */
    PageResult<AdAuditLog> query(String targetType, Long targetId, String action, int pageNum, int pageSize);

}
