package com.dong.ad.support;

/**
 * 广告模块消息主题常量。
 *
 * <p>单独放一份而不是在生产者与消费者里各写一遍字符串：
 * 两边字面量一旦写错，消息会「发送成功但没人消费」，
 * 而本地总线的注册是按 topic 精确匹配的，这种错在日志里只表现为一条 warn。
 */
public final class AdTopics {

    /**
     * 计费事件主题。
     */
    public static final String CHARGE = "ad-charge-event";

    private AdTopics() {
    }

}
