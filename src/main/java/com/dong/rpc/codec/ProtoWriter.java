package com.dong.rpc.codec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * protobuf 写入器：把 tag 与值按线类型拼成字节流。
 *
 * <p>tag 是 {@code 序号 << 3 | 线类型}，解码端先读 tag 才知道后面这段字节该按什么格式读——
 * 这就是 protobuf 能前向兼容的根源：不认识的序号，靠线类型也能算出该跳过多长。
 *
 * <p>fixed32 / fixed64 是<b>小端</b>，与大端的 Java 默认直觉相反，写反了不会报错，
 * 只会得到一个完全错误的浮点值，是这里最容易埋进去的 bug。
 *
 * <p>实现上用 ByteArrayOutputStream 而不是 ByteBuffer：消息长度事先不知道，
 * 而一次 RPC 请求通常只有几百字节，扩容一次的代价远小于先算长度再写一遍。
 */
public final class ProtoWriter {

    /**
     * tag 里线类型占用的位数。
     */
    private static final int WIRE_TYPE_BITS = 3;

    /**
     * 累积的字节。
     */
    private final ByteArrayOutputStream buffer;

    /**
     * 用默认容量构造。
     */
    public ProtoWriter() {
        this(256);
    }

    /**
     * 用指定初始容量构造。
     *
     * @param capacity 初始容量
     */
    public ProtoWriter(int capacity) {
        this.buffer = new ByteArrayOutputStream(capacity);
    }

    /**
     * 写字段头。
     *
     * @param order    字段序号
     * @param wireType 线类型
     * @return 当前写入器
     */
    public ProtoWriter writeTag(int order, WireType wireType) {
        writeVarInt32((order << WIRE_TYPE_BITS) | wireType.getValue());
        return this;
    }

    /**
     * 写 32 位整数，zigzag + varint。
     *
     * @param value 值
     * @return 当前写入器
     */
    public ProtoWriter writeInt32(int value) {
        writeVarInt32(Varints.zigZagEncode(value));
        return this;
    }

    /**
     * 写 64 位整数，zigzag + varint。
     *
     * @param value 值
     * @return 当前写入器
     */
    public ProtoWriter writeInt64(long value) {
        writeVarInt64(Varints.zigZagEncode(value));
        return this;
    }

    /**
     * 写布尔，按 0 / 1 的 varint。
     *
     * @param value 值
     * @return 当前写入器
     */
    public ProtoWriter writeBool(boolean value) {
        writeVarInt32(value ? 1 : 0);
        return this;
    }

    /**
     * 写字符串，UTF-8 字节加长度前缀。
     *
     * @param value 值
     * @return 当前写入器
     */
    public ProtoWriter writeString(String value) {
        return writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 写字节数组，长度前缀加内容。
     *
     * @param value 值
     * @return 当前写入器
     */
    public ProtoWriter writeBytes(byte[] value) {
        writeVarInt32(value.length);
        buffer.write(value, 0, value.length);
        return this;
    }

    /**
     * 写双精度，fixed64 小端。
     *
     * @param value 值
     * @return 当前写入器
     */
    public ProtoWriter writeDouble(double value) {
        writeFixed64(Double.doubleToRawLongBits(value));
        return this;
    }

    /**
     * 写单精度，fixed32 小端。
     *
     * @param value 值
     * @return 当前写入器
     */
    public ProtoWriter writeFloat(float value) {
        writeFixed32(Float.floatToRawIntBits(value));
        return this;
    }

    /**
     * 取已写入的字节。
     *
     * @return 字节数组
     */
    public byte[] toByteArray() {
        return buffer.toByteArray();
    }

    /**
     * 写 varint（32 位）。用无符号右移，负数才会被当成大正数正确拆成 10 字节。
     *
     * @param value 值
     */
    private void writeVarInt32(int value) {
        int remaining = value;
        while (true) {
            if ((remaining & 0xFFFFFF80) == 0) {
                buffer.write(remaining);
                return;
            }
            buffer.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
    }

    /**
     * 写 varint（64 位）。
     *
     * @param value 值
     */
    private void writeVarInt64(long value) {
        long remaining = value;
        while (true) {
            if ((remaining & 0xFFFFFFFFFFFFFF80L) == 0L) {
                buffer.write((int) remaining);
                return;
            }
            buffer.write((int) ((remaining & 0x7FL) | 0x80L));
            remaining >>>= 7;
        }
    }

    /**
     * 写 8 字节定长，小端。
     *
     * @param bits 位模式
     */
    private void writeFixed64(long bits) {
        for (int index = 0; index < 8; index++) {
            buffer.write((int) (bits >>> (8 * index)) & 0xFF);
        }
    }

    /**
     * 写 4 字节定长，小端。
     *
     * @param bits 位模式
     */
    private void writeFixed32(int bits) {
        for (int index = 0; index < 4; index++) {
            buffer.write((bits >>> (8 * index)) & 0xFF);
        }
    }

}
