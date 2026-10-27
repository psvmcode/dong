package com.dong.rpc.codec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * protobuf 读取器。
 *
 * <p>两个必须做对的细节：
 * <ul>
 *   <li><b>读字节必须取 {@code & 0xFF}</b>：Java 的 byte 是有符号的，
 *       直接参与位运算会把高位符号扩展，负值一进来整条消息就解析错了。</li>
 *   <li><b>遇到不认识的字段要跳过而不是报错</b>：跳过的依据就是 tag 里的线类型，
 *       这是 protobuf 前向兼容的实现方式。</li>
 * </ul>
 */
public final class ProtoReader {

    /**
     * varint 最多 10 个字节。
     */
    private static final int MAX_VARINT_BYTES = 10;

    /**
     * 数据。
     */
    private final byte[] data;

    /**
     * 当前位置。
     */
    private int position;

    /**
     * 用数据构造。
     *
     * @param data 待解析的字节
     */
    public ProtoReader(byte[] data) {
        this.data = data;
    }

    /**
     * 是否还有未读字节。
     *
     * @return 有返回 true
     */
    public boolean hasNext() {
        return position < data.length;
    }

    /**
     * 读字段头。
     *
     * @return tag
     */
    public int readTag() {
        return (int) readVarInt64();
    }

    /**
     * 读 32 位整数。
     *
     * @return 值
     */
    public int readInt32() {
        return Varints.zigZagDecode((int) readVarInt64());
    }

    /**
     * 读 64 位整数。
     *
     * @return 值
     */
    public long readInt64() {
        return Varints.zigZagDecode(readVarInt64());
    }

    /**
     * 读布尔。
     *
     * @return 值
     */
    public boolean readBool() {
        return readVarInt64() != 0L;
    }

    /**
     * 读字符串。
     *
     * @return 值
     */
    public String readString() {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 读字节数组。
     *
     * @return 值
     */
    public byte[] readBytes() {
        int length = (int) readVarInt64();
        if (length < 0 || length > data.length - position) {
            throw new ProtoCodecException("length prefixed field exceeds remaining bytes, length=" + length);
        }
        byte[] value = Arrays.copyOfRange(data, position, position + length);
        position += length;
        return value;
    }

    /**
     * 读双精度。
     *
     * @return 值
     */
    public double readDouble() {
        return Double.longBitsToDouble(readFixed64());
    }

    /**
     * 读单精度。
     *
     * @return 值
     */
    public float readFloat() {
        return Float.intBitsToFloat(readFixed32());
    }

    /**
     * 按线类型跳过一个字段。不认识的序号靠这个跳过，
     * 这样服务端加了新字段，老客户端也不会解析失败。
     *
     * @param wireType 线类型
     */
    public void skipField(WireType wireType) {
        switch (wireType) {
            case VARINT -> readVarInt64();
            case FIXED64 -> advance(8);
            // readBytes 自己会读完长度前缀加内容并推进位置，这里只需丢弃结果，不能再 advance 一次
            case LENGTH_DELIMITED -> readBytes();
            case FIXED32 -> advance(4);
            case START_GROUP, END_GROUP -> throw new ProtoCodecException("group wire type is not supported");
        }
    }

    /**
     * 读 varint。
     *
     * @return 值
     */
    private long readVarInt64() {
        long result = 0L;
        for (int index = 0; index < MAX_VARINT_BYTES; index++) {
            if (position >= data.length) {
                throw new ProtoCodecException("unexpected end of message while reading varint");
            }
            int current = data[position++] & 0xFF;
            result |= ((long) (current & 0x7F)) << (7 * index);
            if ((current & 0x80) == 0) {
                return result;
            }
        }
        throw new ProtoCodecException("varint is longer than " + MAX_VARINT_BYTES + " bytes");
    }

    /**
     * 读 8 字节定长，小端。
     *
     * @return 位模式
     */
    private long readFixed64() {
        long bits = 0L;
        for (int index = 0; index < 8; index++) {
            bits |= ((long) (data[position++] & 0xFF)) << (8 * index);
        }
        return bits;
    }

    /**
     * 读 4 字节定长，小端。
     *
     * @return 位模式
     */
    private int readFixed32() {
        int bits = 0;
        for (int index = 0; index < 4; index++) {
            bits |= (data[position++] & 0xFF) << (8 * index);
        }
        return bits;
    }

    /**
     * 前进若干字节。
     *
     * @param length 字节数
     */
    private void advance(int length) {
        if (length < 0 || position + length > data.length) {
            throw new ProtoCodecException("cannot advance " + length + " bytes at position " + position);
        }
        position += length;
    }

}
