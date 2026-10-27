package com.dong.rpc.api;

/**
 * 演示用的回声服务。
 *
 * <p>接口放在 rpc 模块里，实现可以落在任何模块：
 * 调用方编译期只依赖这个接口，运行期由注册中心决定连到谁。
 * 这就是「模块之间通信」能成立的前提——两边不需要互相依赖对方的实现。
 */
public interface EchoService {

    /**
     * 回声。
     *
     * @param text 文本
     * @return 回声结果
     */
    String echo(String text);

    /**
     * 求和。专门用来验证负数：整数字段走 zigzag 编码，
     * 两个负数相加能在一次调用里同时验到「负参数编码」与「负返回值编码」。
     *
     * @param left  左操作数
     * @param right 右操作数
     * @return 和
     */
    int sum(int left, int right);

}
