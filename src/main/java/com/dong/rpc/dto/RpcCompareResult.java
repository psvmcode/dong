package com.dong.rpc.dto;

import lombok.Data;

/**
 * 远程调用与本地调用的对照结果，外加 protobuf 与 JSON 的序列化对比。
 *
 * <p>对照的意义在于把「RPC 到底贵在哪」拆开：
 * 远程减去本地，剩下的就是编码 + 网络 + 排队；
 * 序列化那两行则说明编码本身占多大比例。
 * 没有这组数字，只能笼统地说「RPC 比本地慢」。
 */
@Data
public class RpcCompareResult {

    /**
     * 每种方式各调用多少次。
     */
    private int times;

    /**
     * 远程调用总耗时，毫秒。
     */
    private double remoteTotalMs;

    /**
     * 远程调用平均耗时，毫秒。
     */
    private double remoteAvgMs;

    /**
     * 本地直接调用总耗时，毫秒。
     */
    private double localTotalMs;

    /**
     * 本地直接调用平均耗时，毫秒。
     */
    private double localAvgMs;

    /**
     * 远程相对本地的倍数。
     */
    private double remoteToLocalRatio;

    /**
     * 同一份数据 protobuf 编码后的字节数。
     */
    private int protoBytes;

    /**
     * 同一份数据 JSON 编码后的字节数。
     */
    private int jsonBytes;

    /**
     * JSON 相对 protobuf 的体积倍数。
     */
    private double jsonToProtoRatio;

    /**
     * protobuf 编码耗时，毫秒。
     */
    private double protoEncodeMs;

    /**
     * JSON 编码耗时，毫秒。
     */
    private double jsonEncodeMs;

    /**
     * 编码后再解码，与原始对象是否相等。这是对比的前提——
     * 体积小但解不回原值，再小也没有意义。
     */
    private boolean roundTripEqual;

}
