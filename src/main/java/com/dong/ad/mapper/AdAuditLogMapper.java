package com.dong.ad.mapper;

import com.dong.ad.entity.AdAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 配置变更审计数据访问接口。
 */
@Mapper
public interface AdAuditLogMapper {

    /**
     * 写入审计记录。
     *
     * @param log 审计实体
     * @return 影响行数
     */
    int insert(AdAuditLog log);

    /**
     * 分页查询审计轨迹。
     *
     * @param targetType 目标类型过滤
     * @param targetId   目标 id 过滤
     * @param action     动作过滤
     * @param offset     偏移量
     * @param size       每页大小
     * @return 审计列表
     */
    List<AdAuditLog> selectByPage(@Param("targetType") String targetType, @Param("targetId") Long targetId,
                                  @Param("action") String action, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计分页查询总数。
     *
     * @param targetType 目标类型过滤
     * @param targetId   目标 id 过滤
     * @param action     动作过滤
     * @return 总数
     */
    long countByPage(@Param("targetType") String targetType, @Param("targetId") Long targetId, @Param("action") String action);

}
