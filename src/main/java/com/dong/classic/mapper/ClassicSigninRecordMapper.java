package com.dong.classic.mapper;

import com.dong.classic.entity.ClassicSigninRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
/**
 * 签到流水数据访问。
 */
@Mapper

public interface ClassicSigninRecordMapper {

    /**
     * 插入签到记录。用 insert ignore 而不是普通 insert：
     * 重复签到会撞唯一键，此时应返回 0 而不是报错，签到本身是幂等的。
     */
    int insertIgnore(ClassicSigninRecord record);

    /**
     * 按用户查最近签到记录。
     */
    List<ClassicSigninRecord> selectByUserId(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * 统计某天的签到人数，用于与位图结果对账。
     */
    long countByDate(@Param("signDate") LocalDate signDate);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
