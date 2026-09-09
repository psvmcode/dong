package com.dong.classic.mapper;

import com.dong.classic.entity.ClassicIdGenerated;
import com.dong.classic.entity.ClassicLockLabResult;
import com.dong.classic.entity.ClassicRateLimitLabResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 实验类场景的落库访问：发号器、锁、限流。
 *
 * <p>这三个场景本身是对比实验，数据只存在于 Redis 里会随过期丢失，
 * 落库之后才能回看历史结果，不必每次重新跑一遍。
 * 放在同一个 Mapper 里是因为它们都属于「实验记录」，体量都不大。
 */
@Mapper

public interface ClassicLabRecordMapper {

    /**
     * 记录一次发号批次。
     */
    int insertIdGenerated(ClassicIdGenerated record);

    /**
     * 记录一次锁实验结果。
     */
    int insertLockLabResult(ClassicLockLabResult record);

    /**
     * 记录一条限流算法对比结果。
     */
    int insertRateLimitResult(ClassicRateLimitLabResult record);

    /**
     * 按策略查最近的发号记录，用于对比性能。
     */
    List<ClassicIdGenerated> selectIdGenerated(@Param("strategy") String strategy,
                                               @Param("limit") int limit);

    /**
     * 按模式查最近的锁实验结果。
     */
    List<ClassicLockLabResult> selectLockLabResult(@Param("mode") String mode,
                                                   @Param("limit") int limit);

    /**
     * 按业务键查最近的限流对比结果。
     */
    List<ClassicRateLimitLabResult> selectRateLimitResult(@Param("bizKey") String bizKey,
                                                          @Param("limit") int limit);

}
