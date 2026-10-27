package com.dong.rpc.codec;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * protobuf 的线类型（wire type）。
 *
 * <p>tag 里只有 3 个 bit 放线类型，解码端靠它决定「接下来这段字节怎么读」，
 * 所以线类型必须和字段类型严格绑定；两者对不上时整条消息的偏移就全错了。
 * 本实现不支持 group（START_GROUP / END_GROUP）——官方 protobuf 自己也已废弃它。
 */
@Getter
@RequiredArgsConstructor
public enum WireType {

    /**
     * 变长整数，varint。
     */
    VARINT(0),

    /**
     * 8 字节定长，小端。
     */
    FIXED64(1),

    /**
     * 长度前缀：先一个 varint 长度，再内容。字符串、字节数组、嵌套消息都用它。
     */
    LENGTH_DELIMITED(2),

    /**
     * 组开始，已废弃，解析到就报错。
     */
    START_GROUP(3),

    /**
     * 组结束，已废弃，解析到就报错。
     */
    END_GROUP(4),

    /**
     * 4 字节定长，小端。
     */
    FIXED32(5);

    private final int value;

    /**
     * 按数值反查。
     *
     * @param value 线类型数值
     * @return 线类型
     */
    public static WireType of(int value) {
        for (WireType wireType : values()) {
            if (wireType.value == value) {
                return wireType;
            }
        }
        throw new ProtoCodecException("unsupported wire type " + value);
    }

}
