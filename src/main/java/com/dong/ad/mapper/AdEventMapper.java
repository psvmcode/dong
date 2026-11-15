package com.dong.ad.mapper;

import com.dong.ad.entity.AdEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 投放事件与令牌数据访问接口。
 */
@Mapper
public interface AdEventMapper {

    /**
     * 根据幂等键查询事件。
     *
     * @param eventId 事件幂等键
     * @return 事件
     */
    AdEvent selectByEventId(@Param("eventId") String eventId);

    /**
     * 查询某链路某类型的事件，令牌行就是 REQUEST 类型。
     *
     * @param traceId   链路 id
     * @param eventType 事件类型
     * @return 事件
     */
    AdEvent selectByTraceAndType(@Param("traceId") String traceId, @Param("eventType") int eventType);

    /**
     * 统计某链路某类型的事件数，用于「点击必须先有曝光」与去重前置判断。
     *
     * @param traceId   链路 id
     * @param eventType 事件类型
     * @return 事件数
     */
    long countByTraceAndType(@Param("traceId") String traceId, @Param("eventType") int eventType);

    /**
     * 分页查询事件。
     *
     * @param campaignId 计划过滤
     * @param traceId    链路过滤
     * @param valid      有效标记过滤
     * @param offset     偏移量
     * @param size       每页大小
     * @return 事件列表
     */
    List<AdEvent> selectByPage(@Param("campaignId") Long campaignId, @Param("traceId") String traceId, @Param("valid") Integer valid, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计分页查询总数。
     *
     * @param campaignId 计划过滤
     * @param traceId    链路过滤
     * @param valid      有效标记过滤
     * @return 总数
     */
    long countByPage(@Param("campaignId") Long campaignId, @Param("traceId") String traceId, @Param("valid") Integer valid);

    /**
     * 扫描超时仍处于待计费状态的事件，用于补投丢失的计费消息。
     *
     * @param chargeStatus 待计费状态编码
     * @param before       创建时间早于该时间点
     * @param limit        最大条数
     * @return 事件列表
     */
    List<AdEvent> selectPending(@Param("chargeStatus") int chargeStatus, @Param("before") LocalDateTime before, @Param("limit") int limit);

    /**
     * 查询链路下的全部事件，用于链路诊断。
     *
     * @param traceId 链路 id
     * @param limit   最大条数
     * @return 事件列表
     */
    List<AdEvent> selectByTrace(@Param("traceId") String traceId, @Param("limit") int limit);

    /**
     * 新增事件。uk_event_id 与 uk_trace_type 两条唯一键在这里兜住所有重放。
     *
     * @param event 事件实体
     * @return 影响行数
     */
    int insert(AdEvent event);

    /**
     * 更新事件计费状态。
     *
     * @param id           事件 id
     * @param chargeStatus 目标状态
     * @param rejectReason 处置说明
     * @return 影响行数
     */
    int updateChargeStatus(@Param("id") Long id, @Param("chargeStatus") int chargeStatus, @Param("rejectReason") String rejectReason);

    /**
     * 把补投前的状态重新标为待计费，防止补偿任务重复投同一批。
     *
     * @param id           事件 id
     * @param chargeStatus 目标状态
     * @return 影响行数
     */
    int markPending(@Param("id") Long id, @Param("chargeStatus") int chargeStatus);

}
