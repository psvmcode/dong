package com.dong.classic.service;

import com.dong.classic.dto.NearbyPlaceResponse;

import java.util.List;

/**
 * 地理位置。基于 Redis GEO，本质是把经纬度编码进 ZSet 再做范围查询，
 * 因此附近的人这类功能不需要额外引入空间索引。
 */
public interface GeoService {

    /**
     * 添加坐标。
     */
    Long add(String city, double longitude, double latitude, String member);

    /**
     * 查询指定坐标附近范围内的成员。
     */
    List<NearbyPlaceResponse> nearby(String city, double longitude, double latitude, double radiusKm, int limit);

    /**
     * 计算两个成员之间的距离，单位为公里。
     */
    Double distance(String city, String first, String second);

    /**
     * 移除某个成员。
     */
    void remove(String city, String member);

}
