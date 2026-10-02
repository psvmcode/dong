package com.dong.agent.support.tool;

import java.util.Map;

/**
 * 工具入参提取。schema 只是给模型看的软约束，真正拦住越界值的是这里。
 *
 * <p>调用方是模型，它会编出任何值，所以每个取值都要有默认值与上下界，
 * 越界时收敛到边界而不是把原始值传下去。
 */
public final class ToolArguments {

    /**
     * 禁止实例化。
     */
    private ToolArguments() {
    }

    /**
     * 取整数并收敛到区间内。
     *
     * @param arguments   入参
     * @param key         参数名
     * @param defaultValue 缺失时的默认值
     * @param min         下界
     * @param max         上界
     * @return 区间内的整数
     */
    public static int intIn(Map<String, Object> arguments, String key, int defaultValue, int min, int max) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            try {
                parsed = Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return Math.min(Math.max(parsed, min), max);
    }

    /**
     * 取字符串并截断到最大长度。
     *
     * @param arguments    入参
     * @param key          参数名
     * @param defaultValue 缺失时的默认值
     * @param maxLength    最大长度
     * @return 字符串
     */
    public static String stringIn(Map<String, Object> arguments, String key, String defaultValue, int maxLength) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    /**
     * 取布尔值。
     *
     * @param arguments    入参
     * @param key          参数名
     * @param defaultValue 缺失时的默认值
     * @return 布尔值
     */
    public static boolean booleanIn(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

}
