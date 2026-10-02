package com.dong.agent.support;

/**
 * token 粗估。不引入 tokenizer，按字符类别累加。
 *
 * <p>系数取偏保守的值（中文 1 字算 1.5 token，其它非空白字符算 0.25 token），
 * 估多不估少：宁可提前触发预算闸门，也不要超了才发现。
 * 只用于预算控制，不用于计费。
 */
public final class TokenEstimator {

    /**
     * 中文按 1.5 token 计，放大两倍后是 3 个单位。
     */
    private static final int CJK_UNIT = 3;

    /**
     * 其它非空白字符按 0.25 token 计，放大两倍后是 1 个单位。
     */
    private static final int OTHER_UNIT = 1;

    /**
     * 禁止实例化。
     */
    private TokenEstimator() {
    }

    /**
     * 估算文本的 token 数。
     *
     * @param text 文本，允许为 null
     * @return 估算的 token 数
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int units = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                units += CJK_UNIT;
            } else if (!Character.isWhitespace(c)) {
                units += OTHER_UNIT;
            }
        }
        return units / 2;
    }

}
