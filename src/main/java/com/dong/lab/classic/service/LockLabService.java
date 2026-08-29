package com.dong.lab.classic.service;

import java.util.Map;

public interface LockLabService {

    Map<String, Object> withoutLock(int threads, int loops);

    Map<String, Object> withLock(int threads, int loops);

}
