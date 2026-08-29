package com.dong.lab.classic.service;

import java.time.Duration;
import java.util.List;

public interface DelayQueueService {

    void offer(String payload, Duration delay);

    List<String> take(int limit);

    long size();

}
