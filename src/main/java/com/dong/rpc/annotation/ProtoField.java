package com.dong.rpc.annotation;

import com.dong.rpc.codec.ProtoType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明字段在 protobuf 里的序号与类型。
 *
 * <p><b>order 必须显式写</b>，不写就依赖字段声明顺序；
 * 而字段顺序在重构时会被 IDE 随手改动，线上就会静默解析错乱。
 * 显式序号把这个风险变成启动期就能发现的冲突。
 *
 * <p>type 也必须显式写：Java 的 {@code Integer} 既可能是 int32 也可能是 sint32，
 * 交给推断等于把线上格式交给一次重构。
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtoField {

    /**
     * 字段序号，从 1 开始，同一个消息内不能重复。
     *
     * @return 字段序号
     */
    int order();

    /**
     * protobuf 类型。
     *
     * @return 类型
     */
    ProtoType type();

    /**
     * 是否重复字段（对应 repeated）。字段类型必须是 {@code java.util.List}。
     *
     * @return 是否重复字段
     */
    boolean repeated() default false;

}
