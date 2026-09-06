package com.dong.classic.dto;

/**
 * 附近地点响应。
 */
public class NearbyPlaceResponse {

    /**
     * 地点名称。
     */
    private String member;

    /**
     * 经度。
     */
    private double longitude;

    /**
     * 纬度。
     */
    private double latitude;

    /**
     * 距离（公里）。
     */
    private double distanceKm;

    /**
     * 创建附近地点响应。
     *
     * @param member    地点名称
     * @param longitude 经度
     * @param latitude  纬度
     * @param distanceKm 距离（公里）
     * @return 附近地点响应
     */
    public static NearbyPlaceResponse of(String member, double longitude, double latitude, double distanceKm) {
        NearbyPlaceResponse response = new NearbyPlaceResponse();
        response.setMember(member);
        response.setLongitude(longitude);
        response.setLatitude(latitude);
        response.setDistanceKm(distanceKm);
        return response;
    }

    /**
     * 获取地点名称。
     *
     * @return 地点名称
     */
    public String getMember() {
        return member;
    }

    /**
     * 设置地点名称。
     *
     * @param member 地点名称
     */
    public void setMember(String member) {
        this.member = member;
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
     * 获取距离（公里）。
     *
     * @return 距离（公里）
     */
    public double getDistanceKm() {
        return distanceKm;
    }

    /**
     * 设置距离（公里）。
     *
     * @param distanceKm 距离（公里）
     */
    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

}
