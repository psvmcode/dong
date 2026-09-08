package com.dong.classic.mapper;

import com.dong.classic.entity.ClassicGeoPlace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 地理位置数据访问。
 */
@Mapper

public interface ClassicGeoPlaceMapper {

    /**
     * 写入或更新坐标。同一城市内成员唯一，重复添加视为更新坐标。
     */
    int upsert(@Param("city") String city, @Param("member") String member,
               @Param("longitude") double longitude, @Param("latitude") double latitude);

    /**
     * 按城市查全部坐标，Redis GEO 数据丢失时可据此重建。
     */
    List<ClassicGeoPlace> selectByCity(@Param("city") String city);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
