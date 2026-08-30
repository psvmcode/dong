package com.dong.lab.classic.service;

import com.dong.lab.classic.entity.ShortLink;

/**
 * 短链接。短码由发号器生成后做 Base62 编码，
 * 同一原始链接每次生成的短码都不同，避免被批量遍历。
 */
public interface ShortLinkService {

    /**
     * 生成短链，返回短码。
     */
    String create(String originUrl);

    /**
     * 解析短码得到原始地址，并累加点击数。
     */
    String resolve(String code);

    /**
     * 查询短链详情。
     */
    ShortLink findByCode(String code);

    /**
     * 查询点击次数。
     */
    long hitCount(String code);

}
