package com.dong.lab.cache.service;

import java.util.Map;

public interface CacheLabService {

    Map<String, Object> penetration(int count, boolean guarded);

}
