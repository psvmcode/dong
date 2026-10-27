package com.dong.rpc.dto;

import lombok.Data;

/**
 * 一次调用的结果。成功与失败用同一个结构返回：
 * 对照实验要看的是「这次调用花了多久、成没成」，抛异常就把耗时信息一起丢了。
 */
@Data
public class RpcCallResult {

    /**
     * 是否成功。
     */
    private boolean ok;

    /**
     * 返回值，失败时为空。
     */
    private String result;

    /**
     * 耗时，毫秒，保留三位小数。
     */
    private double elapsedMs;

    /**
     * 失败原因，成功时为空。
     */
    private String error;

}
