package com.dong.lab.classic.dto;

public class NearbyPlaceResponse {

    private String member;

    private double longitude;

    private double latitude;

    private double distanceKm;

    public static NearbyPlaceResponse of(String member, double longitude, double latitude, double distanceKm) {
        NearbyPlaceResponse response = new NearbyPlaceResponse();
        response.setMember(member);
        response.setLongitude(longitude);
        response.setLatitude(latitude);
        response.setDistanceKm(distanceKm);
        return response;
    }

    public String getMember() {
        return member;
    }

    public void setMember(String member) {
        this.member = member;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

}
