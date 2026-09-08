package com.dong.classic.mapper;

import com.dong.classic.entity.ClassicUvDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
/**
 * 每日独立访客数据访问。
 */
@Mapper

public interface ClassicUvDailyMapper {

    /**
     * 写入或更新当日估算值。同一页面同一天只保留一条，
     * 重复统计直接覆盖，因为 HLL 的结果是估算值，累加反而会错。
     */
    int upsert(@Param("page") String page, @Param("statDate") LocalDate statDate,
               @Param("uvCount") long uvCount);

    /**
     * 按页面查历史趋势。
     */
    List<ClassicUvDaily> selectByPage(@Param("page") String page, @Param("limit") int limit);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
