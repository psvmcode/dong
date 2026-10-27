package com.dong.rpc.codec;

import com.dong.rpc.annotation.ProtoMessage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 本轮子支持的 protobuf 字段类型。
 *
 * <p>与官方定义的差异只有一处，而且是刻意选的：
 * {@link #INT32} 与 {@link #INT64} <b>统一按 zigzag 编码</b>（等价于官方的 sint32 / sint64）。
 * 官方 int32 编码负数要固定占 10 个字节，而 RPC 里负数并不少见——错误码、差值、时间戳偏移都是。
 * zigzag 把负数映射成正数再用 varint，-1 只要 1 个字节。
 *
 * <p>这个差异意味着<b>它和官方 protoc 生成的 int32 字段不互通</b>。
 * 真要和外部 protobuf 服务对接，把整数字段声明成 sint32 / sint64 即可对齐。
 */
@Getter
@RequiredArgsConstructor
public enum ProtoType {

    /**
     * 32 位整数，zigzag + varint。
     */
    INT32(WireType.VARINT),

    /**
     * 64 位整数，zigzag + varint。
     */
    INT64(WireType.VARINT),

    /**
     * 布尔，按 varint 的 0 / 1 编码。
     */
    BOOL(WireType.VARINT),

    /**
     * 字符串，UTF-8 字节 + 长度前缀。
     */
    STRING(WireType.LENGTH_DELIMITED),

    /**
     * 字节数组，长度前缀。
     */
    BYTES(WireType.LENGTH_DELIMITED),

    /**
     * 双精度，fixed64 小端。
     */
    DOUBLE(WireType.FIXED64),

    /**
     * 单精度，fixed32 小端。
     */
    FLOAT(WireType.FIXED32),

    /**
     * 嵌套消息，编码后的字节 + 长度前缀。
     */
    MESSAGE(WireType.LENGTH_DELIMITED);

    private final WireType wireType;

    /**
     * 按 Java 类型推断 protobuf 类型。只用于方法参数这类没有注解的地方；
     * 消息内部字段一律以 {@link com.dong.rpc.annotation.ProtoField} 的显式声明为准。
     *
     * @param javaType Java 类型
     * @return protobuf 类型
     */
    public static ProtoType of(Class<?> javaType) {
        if (javaType == null) {
            throw new ProtoCodecException("cannot infer proto type from null");
        }
        if (javaType == String.class) {
            return STRING;
        }
        if (javaType == int.class || javaType == Integer.class || javaType == short.class || javaType == Short.class) {
            return INT32;
        }
        if (javaType == long.class || javaType == Long.class) {
            return INT64;
        }
        if (javaType == boolean.class || javaType == Boolean.class) {
            return BOOL;
        }
        if (javaType == double.class || javaType == Double.class) {
            return DOUBLE;
        }
        if (javaType == float.class || javaType == Float.class) {
            return FLOAT;
        }
        if (javaType == byte[].class) {
            return BYTES;
        }
        if (javaType.isAnnotationPresent(ProtoMessage.class)) {
            return MESSAGE;
        }
        throw new ProtoCodecException("no proto type mapped to " + javaType.getName() + ", declare it with @ProtoField instead");
    }

}
