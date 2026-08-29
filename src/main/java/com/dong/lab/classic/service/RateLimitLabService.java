package com.dong.lab.classic.service;

import java.util.Map;

public interface RateLimitLabService {

    Map<String, Object> compare(String bizKey, long limit, long windowSeconds, int attempts, boolean distributed);

}
