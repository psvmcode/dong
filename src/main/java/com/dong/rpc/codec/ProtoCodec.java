package com.dong.rpc.codec;

import com.dong.rpc.codec.ProtoSchema.FieldMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * protobuf 编解码门面。
 *
 * <p>两类用法：
 * <ul>
 *   <li>{@link #encode(Object)} / {@link #decode(byte[], Class)}：编解码一个 {@code @ProtoMessage} 消息。</li>
 *   <li>{@link #encodeValue(Object)} / {@link #decodeValue(byte[], Class)}：编解码单个方法参数或返回值。
 *       包装成「只有一个字段、序号为 1」的消息，这样参数与消息走同一套编码规则，
 *       不需要为标量再写一套格式——RPC 里参数可能是 String 也可能是复杂对象，统一处理最省事。</li>
 * </ul>
 *
 * <p>空字节数组代表 null。RPC 的返回值与方法参数都允许为 null，
 * 而 protobuf 本身没有「字段不存在」之外的空值表达，这里用「长度为 0」统一表示，
 * 解码端看到空数组就返回 null。
 */
public final class ProtoCodec {

    /**
     * 单值包装时使用的字段序号。
     */
    private static final int VALUE_ORDER = 1;

    /**
     * 单条消息里 repeated 元素的总数上限。帧长上限只约束输入字节，
     * 不约束解码后对象数：2 字节一条的微型 repeated 元素能把一帧放大成
     * 数百万个对象的列表，几个并发请求即可打满堆，必须在解码层封顶。
     */
    private static final int MAX_REPEATED_ELEMENTS = 10_000;

    /**
     * 单帧内允许出现的字段总数上限。跳过未知字段和非 repeated 字段也要计数，
     * 否则一个塞满微型字段的合法帧能把 EventLoop 的 CPU 占满。
     */
    private static final int MAX_FIELDS = 100_000;

    /**
     * 空字节。
     */
    private static final byte[] EMPTY = new byte[0];

    private ProtoCodec() {
    }

    /**
     * 编码消息。
     *
     * @param message 消息对象
     * @return 字节
     */
    public static byte[] encode(Object message) {
        if (message == null) {
            return EMPTY;
        }
        ProtoSchema schema = ProtoSchema.of(message.getClass());
        ProtoWriter writer = new ProtoWriter();
        for (FieldMeta field : schema.fields()) {
            Object value = field.get(message);
            if (value == null) {
                continue;
            }
            if (field.repeated()) {
                for (Object item : (Iterable<?>) value) {
                    if (item != null) {
                        writer.writeTag(field.order(), field.type().getWireType());
                        writeValue(writer, field.type(), item);
                    }
                }
                continue;
            }
            writer.writeTag(field.order(), field.type().getWireType());
            writeValue(writer, field.type(), value);
        }
        return writer.toByteArray();
    }

    /**
     * 解码消息。空字节返回 null。
     *
     * @param data 字节
     * @param type 消息类型
     * @param <T>  消息类型
     * @return 消息对象
     */
    public static <T> T decode(byte[] data, Class<T> type) {
        if (data == null || data.length == 0) {
            return null;
        }
        ProtoSchema schema = ProtoSchema.of(type);
        Object target = schema.newInstance();
        ProtoReader reader = new ProtoReader(data);
        int repeatedElements = 0;
        int fields = 0;
        while (reader.hasNext()) {
            if (++fields > MAX_FIELDS) {
                throw new ProtoCodecException("fields exceed " + MAX_FIELDS + ", possible malicious payload");
            }
            int tag = reader.readTag();
            int order = tag >>> 3;
            WireType wireType = WireType.of(tag & 7);
            FieldMeta field = schema.byOrder(order);
            // 未知序号：对方用了本地 schema 里没有的字段，跳过即可（前向兼容）
            // 线类型不符：对方改过这个字段的类型，继续读会把后面的字段全部读错，只能跳过
            if (field == null || field.type().getWireType() != wireType) {
                reader.skipField(wireType);
                continue;
            }
            Object value = readValue(reader, field.type(), field.valueType());
            if (field.repeated()) {
                if (++repeatedElements > MAX_REPEATED_ELEMENTS) {
                    throw new ProtoCodecException("repeated elements exceed " + MAX_REPEATED_ELEMENTS + ", possible malicious payload");
                }
                appendRepeated(target, field, value);
            } else {
                field.set(target, value);
            }
        }
        return type.cast(target);
    }

    /**
     * 编码单个值，用于方法参数与返回值。null 编成空数组。
     *
     * @param value 值
     * @return 字节
     */
    public static byte[] encodeValue(Object value) {
        if (value == null) {
            return EMPTY;
        }
        ProtoType type = ProtoType.of(value.getClass());
        ProtoWriter writer = new ProtoWriter(32);
        writer.writeTag(VALUE_ORDER, type.getWireType());
        writeValue(writer, type, value);
        return writer.toByteArray();
    }

    /**
     * 解码单个值。空字节返回 null。
     *
     * @param data     字节
     * @param javaType 目标 Java 类型
     * @return 值
     */
    public static Object decodeValue(byte[] data, Class<?> javaType) {
        if (data == null || data.length == 0) {
            return null;
        }
        ProtoType type = ProtoType.of(javaType);
        ProtoReader reader = new ProtoReader(data);
        int tag = reader.readTag();
        WireType wireType = WireType.of(tag & 7);
        if (wireType != type.getWireType()) {
            throw new ProtoCodecException("wire type mismatch: expected " + type.getWireType() + " but got " + wireType);
        }
        return readValue(reader, type, javaType);
    }

    /**
     * 按 protobuf 类型写一个值。
     *
     * @param writer 写入器
     * @param type   protobuf 类型
     * @param value  值
     */
    private static void writeValue(ProtoWriter writer, ProtoType type, Object value) {
        switch (type) {
            case INT32 -> writer.writeInt32(((Number) value).intValue());
            case INT64 -> writer.writeInt64(((Number) value).longValue());
            case BOOL -> writer.writeBool((Boolean) value);
            case STRING -> writer.writeString((String) value);
            case BYTES -> writer.writeBytes((byte[]) value);
            case DOUBLE -> writer.writeDouble((Double) value);
            case FLOAT -> writer.writeFloat((Float) value);
            case MESSAGE -> writer.writeBytes(encode(value));
        }
    }

    /**
     * 按 protobuf 类型读一个值。
     *
     * @param reader   读取器
     * @param type     protobuf 类型
     * @param javaType 目标 Java 类型
     * @return 值
     */
    private static Object readValue(ProtoReader reader, ProtoType type, Class<?> javaType) {
        return switch (type) {
            case INT32 -> reader.readInt32();
            case INT64 -> reader.readInt64();
            case BOOL -> reader.readBool();
            case STRING -> reader.readString();
            case BYTES -> reader.readBytes();
            case DOUBLE -> reader.readDouble();
            case FLOAT -> reader.readFloat();
            case MESSAGE -> decode(reader.readBytes(), javaType);
        };
    }

    /**
     * 往重复字段里追加一个元素。
     *
     * @param target 目标对象
     * @param field  字段元数据
     * @param value  元素值
     */
    @SuppressWarnings("unchecked")
    private static void appendRepeated(Object target, FieldMeta field, Object value) {
        Object current = field.get(target);
        List<Object> list;
        if (current instanceof List<?> existing) {
            list = (List<Object>) existing;
        } else {
            list = new ArrayList<>();
            field.set(target, list);
        }
        list.add(value);
    }

}
