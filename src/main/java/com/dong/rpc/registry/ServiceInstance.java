package com.dong.rpc.registry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一个服务实例。
 *
 * <p>只带服务名与地址，不带方法列表：注册中心回答的是「谁提供了这个服务」，
 * 不是「这个服务有什么方法」。把方法元数据塞进注册中心，
 * 每加一个方法就要刷一次全量注册信息，是常见的过度设计。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInstance {

    /**
     * 服务名。
     */
    private String serviceName;

    /**
     * 主机。
     */
    private String host;

    /**
     * 端口。
     */
    private int port;

    /**
     * 地址，形如 host:port。
     *
     * @return 地址
     */
    public String address() {
        return host + ":" + port;
    }

    /**
     * 按服务名与地址解析实例。
     *
     * @param serviceName 服务名
     * @param address     地址，形如 host:port
     * @return 实例
     */
    public static ServiceInstance parse(String serviceName, String address) {
        int separator = address.lastIndexOf(':');
        if (separator <= 0) {
            throw new IllegalArgumentException("illegal service address " + address);
        }
        String host = address.substring(0, separator);
        int port = Integer.parseInt(address.substring(separator + 1));
        return new ServiceInstance(serviceName, host, port);
    }

}
