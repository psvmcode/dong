package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 附近位置查询请求。底层是 Redis GEO，
 * 本质是把经纬度编码进 ZSet 再做范围查询，半径单位是公里。
 */
public class GeoNearbyQueryRequest {

    /**
     * 城市 key。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String city = "beijing";

    /**
     * 经度。
     */
    @Min(-180)
    @Max(180)
    private double longitude;

    /**
     * 纬度。
     */
    @Min(-90)
    @Max(90)
    private double latitude;

    /**
     * 半径，单位公里。
     */
    @Min(0)
    @Max(20_000)
    private double radiusKm = 5;

    /**
     * 返回条数。
     */
    @Min(1)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int limit = 10;

    /**
     * 获取城市 key。
     *
     * @return 城市 key
     */
    public String getCity() {
        return city;
    }

    /**
     * 设置城市 key。
     *
     * @param city 城市 key
     */
    public void setCity(String city) {
        this.city = city;
    }

    /**
     * 获取经度。
     *
     * @return 经度
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * 设置经度。
     *
     * @param longitude 经度
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * 获取纬度。
     *
     * @return 纬度
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * 设置纬度。
     *
     * @param latitude 纬度
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * 获取半径。
     *
     * @return 半径，单位公里
     */
    public double getRadiusKm() {
        return radiusKm;
    }

    /**
     * 设置半径。
     *
     * @param radiusKm 半径，单位公里
     */
    public void setRadiusKm(double radiusKm) {
        this.radiusKm = radiusKm;
    }

    /**
     * 获取返回条数。
     *
     * @return 返回条数
     */
    public int getLimit() {
        return limit;
    }

    /**
     * 设置返回条数。
     *
     * @param limit 返回条数
     */
    public void setLimit(int limit) {
        this.limit = limit;
    }

}
