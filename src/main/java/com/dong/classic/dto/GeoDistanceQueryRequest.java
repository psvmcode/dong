package com.dong.classic.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 两点距离查询请求。距离由 Redis 直接算，不需要把坐标取回来自己算。
 */
public class GeoDistanceQueryRequest {

    /**
     * 城市 key。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String city = "beijing";

    /**
     * 第一个成员。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String first;

    /**
     * 第二个成员。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String second;

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
     * 获取第一个成员。
     *
     * @return 成员名
     */
    public String getFirst() {
        return first;
    }

    /**
     * 设置第一个成员。
     *
     * @param first 成员名
     */
    public void setFirst(String first) {
        this.first = first;
    }

    /**
     * 获取第二个成员。
     *
     * @return 成员名
     */
    public String getSecond() {
        return second;
    }

    /**
     * 设置第二个成员。
     *
     * @param second 成员名
     */
    public void setSecond(String second) {
        this.second = second;
    }

}
