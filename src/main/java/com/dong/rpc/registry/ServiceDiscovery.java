package com.dong.rpc.registry;

import java.util.List;

/**
 * 服务发现。客户端用它按服务名找到可以连的地址。
 *
 * <p>返回列表而不是单个地址：同一个服务通常有多个实例，
 * 挑哪一个属于负载均衡的事，不该由注册中心替调用方决定。
 */
public interface ServiceDiscovery {

    /**
     * 发现一个服务的可用实例。
     *
     * @param serviceName 服务名
     * @return 实例列表，没有可用实例时返回空列表
     */
    List<ServiceInstance> discover(String serviceName);

}
