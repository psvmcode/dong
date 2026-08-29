package com.dong.lab.classic.service;

import com.dong.lab.classic.entity.ShortLink;

public interface ShortLinkService {

    String create(String originUrl);

    String resolve(String code);

    ShortLink findByCode(String code);

    long hitCount(String code);

}
