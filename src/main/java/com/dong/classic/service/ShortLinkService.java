package com.dong.classic.service;

import com.dong.classic.entity.ShortLink;

/**
 * 短链接。短码由发号器生成后做 Base62 编码，
 * 同一原始链接每次生成的短码都不同，避免被批量遍历。
 */
public interface ShortLinkService {

    /**
     * 生成短链，返回短码。
     *
     * @param originUrl     原始链接
     * @param expireMinutes 有效分钟数，小于等于 0 表示长期有效
     * @return 短码
     */
    String create(String originUrl, long expireMinutes);

    /**
     * 解析短码得到原始地址，并累加点击数。
     *
     * @param code 短码
     * @return 原始链接
     */
    String resolve(String code);

    /**
     * 查询短链详情。
     *
     * @param code 短码
     * @return 短链实体
     */
    ShortLink findByCode(String code);

    /**
     * 查询点击次数，优先取缓存累加器，回落数据库落库值。
     *
     * @param code 短码
     * @return 点击次数
     */
    long hitCount(String code);

    /**
     * 启停短链。停用后拒绝跳转，并同步删除缓存使其立即生效。
     *
     * @param code    短码
     * @param enabled 是否启用
     */
    void toggle(String code, boolean enabled);

    /**
     * 把缓存中的点击计数回写数据库，由定时任务调用。
     *
     * @return 本轮回写的短链条数
     */
    int flushHitCount();

}
