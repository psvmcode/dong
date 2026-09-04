package com.dong.lab.classic.dto;

/**
 * NearbyPlaceResponse。
 */
public class NearbyPlaceResponse {

    /**
     * member。
     */
    private String member;

    /**
     * longitude。
     */
    private double longitude;

    /**
     * latitude。
     */
    private double latitude;

    /**
     * distanceKm。
     */
    private double distanceKm;

    /**
     * of。
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
     * getMember。
     */
    public String getMember() {
        return member;
    }

    /**
     * setMember。
     */
    public void setMember(String member) {
        this.member = member;
    }

    /**
     * getLongitude。
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * setLongitude。
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * getLatitude。
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * setLatitude。
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * getDistanceKm。
     */
    public double getDistanceKm() {
        return distanceKm;
    }

    /**
     * setDistanceKm。
     */
    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

}
