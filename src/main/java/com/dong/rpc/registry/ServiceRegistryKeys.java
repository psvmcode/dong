package com.dong.rpc.registry;

import com.dong.common.constant.Constants;

/**
 * 注册中心在 Redis 里的 key。
 *
 * <p>收在一处是因为注册与发现是两个类、却必须算出同一个 key；
 * 各拼一份字符串，改前缀时必然漏掉一处，而漏掉的表现是「注册成功却永远发现不了」。
 */
public final class ServiceRegistryKeys {

    /**
     * key 前缀。
     */
    private static final String PREFIX = Constants.REDIS_PREFIX + Constants.SEPARATOR + "rpc" + Constants.SEPARATOR + "svc" + Constants.SEPARATOR;

    private ServiceRegistryKeys() {
    }

    /**
     * 一个服务对应的 key。
     *
     * @param serviceName 服务名
     * @return key
     */
    public static String of(String serviceName) {
        return PREFIX + serviceName;
    }

}
