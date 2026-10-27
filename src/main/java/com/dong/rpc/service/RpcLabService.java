package com.dong.rpc.service;

import com.dong.rpc.dto.ProductQueryRequest;
import com.dong.rpc.dto.ProductQueryResponse;
import com.dong.rpc.dto.RpcCallResult;
import com.dong.rpc.dto.RpcCompareResult;
import com.dong.rpc.dto.RegistrySnapshot;
import com.dong.rpc.dto.RpcStatsResponse;

/**
 * RPC 对照实验。
 *
 * <p>存在的理由：RPC 的代价平时是感觉不到的，因为调用方写的代码和本地调用一模一样。
 * 只有把「远程」和「本地」并排跑一遍，才知道自己每次跨服务调用到底付了什么。
 */
public interface RpcLabService {

    /**
     * 远程调用回声服务，走完整的 编码 → TCP → 解码 → 调用 → 回程。
     *
     * @param text 文本
     * @return 调用结果
     */
    RpcCallResult echoRemote(String text);

    /**
     * 本地直接调用同一个实现，作为对照组。
     *
     * @param text 文本
     * @return 调用结果
     */
    RpcCallResult echoLocal(String text);

    /**
     * 远程求和，用负数验证 zigzag 编码。
     *
     * @param left  左操作数
     * @param right 右操作数
     * @return 调用结果，结果是和的字符串形式
     */
    RpcCallResult sumRemote(int left, int right);

    /**
     * 远程与本地的耗时对照，外加 protobuf 与 JSON 的序列化对比。
     *
     * @param text  文本
     * @param times 每种方式调用多少次
     * @return 对照结果
     */
    RpcCompareResult compare(String text, int times);

    /**
     * 注册表快照。
     *
     * @return 快照
     */
    RegistrySnapshot registry();

    /**
     * 运行状况。
     *
     * @return 运行状况
     */
    RpcStatsResponse stats();

    /**
     * 跨模块调用商品查询服务，实现在 search 模块。
     *
     * @param request 查询条件
     * @return 查询结果
     */
    ProductQueryResponse queryProduct(ProductQueryRequest request);

}
