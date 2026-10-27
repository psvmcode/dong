package com.dong.rpc.codec;

import com.dong.rpc.annotation.ProtoField;
import com.dong.rpc.annotation.ProtoMessage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 一个类的 protobuf schema，由注解解析而来并缓存。
 *
 * <p>反射只在第一次遇到这个类时走一遍：RPC 是高频路径，
 * 每条消息都去 getDeclaredFields + 解析注解，光反射开销就能盖过编解码本身。
 *
 * <p>schema 在首次使用时构建而不是启动时扫描全部类：
 * 前者只为自己真正用到的类付代价，后者还得解决「扫哪些包」的问题。
 * 代价是错误发现得晚一点——序号冲突要等第一次编解码才暴露。
 */
public final class ProtoSchema {

    /**
     * 类到 schema 的缓存。
     */
    private static final ConcurrentMap<Class<?>, ProtoSchema> CACHE = new ConcurrentHashMap<>();

    /**
     * 消息类型。
     */
    private final Class<?> type;

    /**
     * 字段元数据，按序号升序。写入顺序固定，同样的对象才会编出同样的字节。
     */
    private final List<FieldMeta> fields;

    /**
     * 序号到字段的映射。
     */
    private final Map<Integer, FieldMeta> byOrder;

    /**
     * 无参构造，用于解码时创建实例。
     */
    private final Constructor<?> constructor;

    private ProtoSchema(Class<?> type, List<FieldMeta> fields, Constructor<?> constructor) {
        this.type = type;
        this.fields = fields;
        this.constructor = constructor;
        Map<Integer, FieldMeta> mapping = new LinkedHashMap<>();
        for (FieldMeta field : fields) {
            if (mapping.put(field.order(), field) != null) {
                // 序号重复会让一方覆盖另一方，且只在编解码时才表现出来，必须在建 schema 时就拦下
                throw new ProtoCodecException(type.getName() + " declares duplicate field order " + field.order());
            }
        }
        this.byOrder = mapping;
    }

    /**
     * 取一个类的 schema，带缓存。
     *
     * @param type 消息类型
     * @return schema
     */
    public static ProtoSchema of(Class<?> type) {
        return CACHE.computeIfAbsent(type, ProtoSchema::build);
    }

    /**
     * 消息类型。
     *
     * @return 类型
     */
    public Class<?> type() {
        return type;
    }

    /**
     * 全部字段。
     *
     * @return 字段元数据
     */
    public List<FieldMeta> fields() {
        return fields;
    }

    /**
     * 按序号查字段，查不到返回 null（说明对方用了本地 schema 里没有的字段）。
     *
     * @param order 字段序号
     * @return 字段元数据
     */
    public FieldMeta byOrder(int order) {
        return byOrder.get(order);
    }

    /**
     * 创建一个空实例。
     *
     * @return 实例
     */
    public Object newInstance() {
        try {
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new ProtoCodecException("cannot instantiate " + type.getName() + ", it needs a no-arg constructor", ex);
        }
    }

    /**
     * 解析一个类的 schema。
     *
     * @param type 消息类型
     * @return schema
     */
    private static ProtoSchema build(Class<?> type) {
        if (!type.isAnnotationPresent(ProtoMessage.class)) {
            throw new ProtoCodecException(type.getName() + " is not annotated with @ProtoMessage");
        }
        List<FieldMeta> metas = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
                    continue;
                }
                ProtoField annotation = field.getAnnotation(ProtoField.class);
                if (annotation == null) {
                    continue;
                }
                metas.add(metaOf(annotation, field));
            }
        }
        if (metas.isEmpty()) {
            throw new ProtoCodecException(type.getName() + " declares no @ProtoField");
        }
        metas.sort((left, right) -> Integer.compare(left.order(), right.order()));
        Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (NoSuchMethodException ex) {
            throw new ProtoCodecException(type.getName() + " needs a no-arg constructor for decoding", ex);
        }
        return new ProtoSchema(type, List.copyOf(metas), constructor);
    }

    /**
     * 构造字段元数据并做校验。
     *
     * @param annotation 字段注解
     * @param field      字段
     * @return 字段元数据
     */
    private static FieldMeta metaOf(ProtoField annotation, Field field) {
        int order = annotation.order();
        if (order <= 0) {
            throw new ProtoCodecException("field " + field.getName() + " has a non-positive order " + order);
        }
        ProtoType protoType = annotation.type();
        boolean repeated = annotation.repeated();
        Class<?> valueType = field.getType();
        if (repeated) {
            if (!List.class.isAssignableFrom(valueType)) {
                throw new ProtoCodecException("repeated field " + field.getName() + " must be a List");
            }
            valueType = elementTypeOf(field);
        }
        if (protoType == ProtoType.MESSAGE && !valueType.isAnnotationPresent(ProtoMessage.class)) {
            throw new ProtoCodecException("message field " + field.getName() + " points to a type without @ProtoMessage");
        }
        field.setAccessible(true);
        return new FieldMeta(order, protoType, repeated, field, valueType);
    }

    /**
     * 取 List 字段的元素类型。
     *
     * @param field 字段
     * @return 元素类型
     */
    private static Class<?> elementTypeOf(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class<?> element) {
                return element;
            }
        }
        throw new ProtoCodecException("repeated field " + field.getName() + " must declare its element type, e.g. List<String>");
    }

    /**
     * 字段元数据。
     *
     * @param order     字段序号
     * @param type      protobuf 类型
     * @param repeated  是否重复字段
     * @param field     反射字段
     * @param valueType 值的类型；重复字段时是元素类型
     */
    public record FieldMeta(int order, ProtoType type, boolean repeated, Field field, Class<?> valueType) {

        /**
         * 取值。
         *
         * @param target 目标对象
         * @return 值
         */
        public Object get(Object target) {
            try {
                return field.get(target);
            } catch (IllegalAccessException ex) {
                throw new ProtoCodecException("cannot read field " + field.getName(), ex);
            }
        }

        /**
         * 设值。
         *
         * @param target 目标对象
         * @param value  值
         */
        public void set(Object target, Object value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException ex) {
                throw new ProtoCodecException("cannot write field " + field.getName(), ex);
            }
        }

    }

}
