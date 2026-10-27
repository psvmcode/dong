package com.dong.rpc.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类是可被 protobuf 编解码的消息。
 *
 * <p>没有 protoc，也没有引入官方运行时，schema 由注解描述：
 * 类上标这个注解，字段上标 {@link ProtoField} 声明序号与类型，
 * 编解码器在首次遇到这个类时反射一次并把结果缓存起来。
 *
 * <p>与官方 protobuf 的语义对齐的部分：
 * <ul>
 *   <li>字段序号从 1 开始，一旦发布就不能改——线上出现过的序号被复用，
 *       新老节点之间的数据就会互相解析错。</li>
 *   <li>值为 null 或默认值时不编码（proto3 语义），解码端拿到的是字段类型的零值。
 *       代价是「没传」和「传了默认值」无法区分，RPC 场景里这个区别通常不重要。</li>
 *   <li>遇到不认识的字段序号直接跳过而不是报错，这是前向兼容的来源：
 *       服务端加字段、客户端不升级也能正常工作。</li>
 * </ul>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtoMessage {
}
