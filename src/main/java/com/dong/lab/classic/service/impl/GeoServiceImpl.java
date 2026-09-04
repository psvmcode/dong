package com.dong.lab.classic.service.impl;

import com.dong.lab.classic.dto.NearbyPlaceResponse;
import com.dong.lab.classic.service.GeoService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.GeoOrder;
import org.redisson.api.GeoPosition;
import org.redisson.api.GeoUnit;
import org.redisson.api.RGeo;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * 地理位置实现。基于 Redis GEO，
 * 本质是把经纬度编码进 ZSet 再做范围查询。
 */
@Slf4j
@Service

public class GeoServiceImpl implements GeoService {

    private static final String GEO = "lab:geo:";

    /**
     * Redisson 客户端。
     */
    private final RedissonClient redissonClient;

    public GeoServiceImpl(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 添加地理位置坐标。
     *
     * @param city      城市
     * @param longitude 经度
     * @param latitude  纬度
     * @param member    成员标识
     * @return 新增数量
     */
    @Override
    public Long add(String city, double longitude, double latitude, String member) {
        Long added = geoOf(city).add(longitude, latitude, member);
        log.info("geo added city={} member={}", city, member);
        return added;
    }

    /**
     * 查询指定坐标附近范围内的成员。
     *
     * @param city      城市
     * @param longitude 经度
     * @param latitude  纬度
     * @param radiusKm  半径（公里）
     * @param limit     最大返回数量
     * @return 附近地点列表
     */
    @Override
    public List<NearbyPlaceResponse> nearby(String city, double longitude, double latitude, double radiusKm, int limit) {
        RGeo<String> geo = geoOf(city);
        Map<String, Double> distances =
                geo.radiusWithDistance(longitude, latitude, radiusKm, GeoUnit.KILOMETERS, GeoOrder.ASC, limit);
        Map<String, GeoPosition> positions = geo.pos(distances.keySet().toArray(new String[0]));
        List<NearbyPlaceResponse> places = new ArrayList<>(distances.size());
        for (Map.Entry<String, Double> entry : distances.entrySet()) {
            GeoPosition position = positions.get(entry.getKey());
            places.add(NearbyPlaceResponse.of(entry.getKey(),
                    position == null ? 0D : position.getLongitude(),
                    position == null ? 0D : position.getLatitude(),
                    entry.getValue()));
        }
        return places;
    }

    /**
     * 计算两个成员之间的距离。
     *
     * @param city   城市
     * @param first  第一个成员
     * @param second 第二个成员
     * @return 距离（公里）或 null
     */
    @Override
    public Double distance(String city, String first, String second) {
        return geoOf(city).dist(first, second, GeoUnit.KILOMETERS);
    }

    /**
     * 移除某个成员。
     *
     * @param city   城市
     * @param member 成员标识
     */
    @Override
    public void remove(String city, String member) {
        geoOf(city).remove(member);
    }

    /**
     * 获取指定城市的 GEO 集合。
     *
     * @param city 城市
     * @return GEO 集合
     */
    private RGeo<String> geoOf(String city) {
        return redissonClient.getGeo(GEO + city);
    }

}
