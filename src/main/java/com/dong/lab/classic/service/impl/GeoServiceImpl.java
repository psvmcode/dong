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

@Slf4j
@Service
public class GeoServiceImpl implements GeoService {

    private static final String GEO = "lab:geo:";

    private final RedissonClient redissonClient;

    public GeoServiceImpl(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public Long add(String city, double longitude, double latitude, String member) {
        Long added = geoOf(city).add(longitude, latitude, member);
        log.info("geo added city={} member={}", city, member);
        return added;
    }

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

    @Override
    public Double distance(String city, String first, String second) {
        return geoOf(city).dist(first, second, GeoUnit.KILOMETERS);
    }

    @Override
    public void remove(String city, String member) {
        geoOf(city).remove(member);
    }

    private RGeo<String> geoOf(String city) {
        return redissonClient.getGeo(GEO + city);
    }

}
