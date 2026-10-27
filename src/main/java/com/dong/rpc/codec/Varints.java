package com.dong.rpc.codec;

/**
 * varint 与 zigzag 的双向变换。
 *
 * <p>zigzag 的作用是把有符号整数映射成无符号：{@code 0→0, -1→1, 1→2, -2→3, 2→4……}
 * 映射之后小负数也只需要一个字节，这正是官方 int32 编码负数要占 10 个字节、
 * 而 sint32 只占 1 个的原因。本轮子对整型统一走 zigzag，见 {@link ProtoType} 的说明。
 */
public final class Varints {

    private Varints() {
    }

    /**
     * 32 位 zigzag 编码。
     *
     * @param value 原值
     * @return 编码后的值
     */
    public static int zigZagEncode(int value) {
        return (value << 1) ^ (value >> 31);
    }

    /**
     * 64 位 zigzag 编码。
     *
     * @param value 原值
     * @return 编码后的值
     */
    public static long zigZagEncode(long value) {
        return (value << 1) ^ (value >> 63);
    }

    /**
     * 32 位 zigzag 解码。
     *
     * @param value 编码后的值
     * @return 原值
     */
    public static int zigZagDecode(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    /**
     * 64 位 zigzag 解码。
     *
     * @param value 编码后的值
     * @return 原值
     */
    public static long zigZagDecode(long value) {
        return (value >>> 1) ^ -(value & 1L);
    }

}
