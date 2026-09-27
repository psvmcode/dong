package com.dong.search.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * 附近商品检索请求。半径上限两万公里，超过这个数已经能覆盖地球任意两点。
 */
public class NearbySearchQueryRequest {

    /**
     * 纬度。
     */
    @DecimalMin("-90")
    @DecimalMax("90")
    private double lat;

    /**
     * 经度。
     */
    @DecimalMin("-180")
    @DecimalMax("180")
    private double lon;

    /**
     * 半径，单位公里。
     */
    @Positive
    @Max(20_000)
    private double radiusKm = 10;

    /**
     * 返回条数。
     */
    @Min(1)
    @Max(Constants.MAX_PAGE_SIZE)
    private int size = Constants.DEFAULT_PAGE_SIZE;

    /**
     * 获取纬度。
     *
     * @return 纬度
     */
    public double getLat() {
        return lat;
    }

    /**
     * 设置纬度。
     *
     * @param lat 纬度
     */
    public void setLat(double lat) {
        this.lat = lat;
    }

    /**
     * 获取经度。
     *
     * @return 经度
     */
    public double getLon() {
        return lon;
    }

    /**
     * 设置经度。
     *
     * @param lon 经度
     */
    public void setLon(double lon) {
        this.lon = lon;
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
    public int getSize() {
        return size;
    }

    /**
     * 设置返回条数。
     *
     * @param size 返回条数
     */
    public void setSize(int size) {
        this.size = size;
    }

}
