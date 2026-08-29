package com.dong.lab.classic.service;

import java.util.Map;

public interface IdGeneratorService {

    Map<String, Object> generate(String strategy, int count);

}
