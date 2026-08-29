package com.dong.lab.classic.service;

import com.dong.lab.classic.dto.NearbyPlaceResponse;

import java.util.List;

public interface GeoService {

    Long add(String city, double longitude, double latitude, String member);

    List<NearbyPlaceResponse> nearby(String city, double longitude, double latitude, double radiusKm, int limit);

    Double distance(String city, String first, String second);

    void remove(String city, String member);

}
