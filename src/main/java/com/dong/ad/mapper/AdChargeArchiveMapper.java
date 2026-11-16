package com.dong.ad.mapper;

import com.dong.ad.entity.AdChargeArchive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 计费流水归档数据访问接口。
 */
@Mapper
public interface AdChargeArchiveMapper {

    /**
     * 写入归档行，唯一键保证重复归档幂等。
     *
     * @param archive 归档实体
     * @return 影响行数
     */
    int insert(AdChargeArchive archive);

    /**
     * 按幂等键统计归档行，避免重复搬运。
     *
     * @param eventId 事件幂等键
     * @return 归档行数
     */
    long countByEventId(@Param("eventId") String eventId);

}
