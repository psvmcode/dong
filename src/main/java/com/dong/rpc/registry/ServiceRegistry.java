package com.dong.rpc.registry;

import java.util.Collection;

/**
 * 服务注册。服务端用它把自己暴露出去。
 *
 * <p>注册是「心跳续约」而不是「一次注册长期有效」：
 * 进程被 kill -9 时没有人来得及执行注销，注册中心里会留下一个永远存在、
 * 却永远连不上的地址。带 TTL 的续约把这个问题变成「最多错 ttl 毫秒」。
 *
 * <p>代价是客户端必须能容忍发现到已死实例——所以调用失败要有明确的状态返回，
 * 而不是让客户端以为「服务在，只是这次失败了」。
 */
public interface ServiceRegistry {

    /**
     * 注册一个实例。
     *
     * @param instance 实例
     */
    void register(ServiceInstance instance);

    /**
     * 注销一个实例，正常停机时调用。
     *
     * @param instance 实例
     */
    void unregister(ServiceInstance instance);

    /**
     * 续约一批实例，顺带清理超时的历史成员。
     *
     * @param instances 本进程暴露的全部实例
     * @return 续约成功的条数
     */
    int renew(Collection<ServiceInstance> instances);

}
