package com.dong.rpc.dto;

import lombok.Data;

/**
 * 求和调用请求。默认就是两个负数，用来验证 zigzag 编码：
 * 官方 int32 编码负数要 10 个字节，zigzag 只要 1 个。
 */
@Data
public class RpcSumRequest {

    /**
     * 左操作数。
     */
    private int left = -128;

    /**
     * 右操作数。
     */
    private int right = -256;

}
