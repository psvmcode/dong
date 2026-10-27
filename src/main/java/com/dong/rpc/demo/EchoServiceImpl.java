package com.dong.rpc.demo;

import com.dong.rpc.annotation.RpcService;
import com.dong.rpc.api.EchoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 回声服务的实现，rpc 模块自带，不依赖任何中间件。
 *
 * <p>自带一份实现的意义：验证 RPC 链路时不应该依赖外部组件是否可用。
 * 任何环境下启动就能调通，出问题就能确定是 RPC 本身的问题。
 *
 * <p>它同时被注册成普通 Spring Bean，于是同一份实现既能被远程调用，
 * 也能被本地直接调用——这正是「远程 vs 本地」对照实验需要的两个入口。
 */
@Slf4j
@Service
@RpcService(interfaceClass = EchoService.class)
public class EchoServiceImpl implements EchoService {

    @Override
    public String echo(String text) {
        return "echo:" + text;
    }

    @Override
    public int sum(int left, int right) {
        return left + right;
    }

}
