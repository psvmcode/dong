package com.dong.rpc.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 注册表快照：本进程暴露了哪些服务，注册中心里又能发现到哪些实例。
 *
 * <p>两边都列出来是为了回答那个最常见的问题——
 * 「服务明明起来了，为什么调不通」：暴露列表里没有，是没打注解或没被扫描；
 * 暴露了却发现不到，是注册中心的问题。
 */
@Data
public class RegistrySnapshot {

    /**
     * 本进程暴露的服务名。
     */
    private List<String> exposedServices;

    /**
     * 服务名到已发现实例地址的映射。
     */
    private Map<String, List<String>> discovered;

}
