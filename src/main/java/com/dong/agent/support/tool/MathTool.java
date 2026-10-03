package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 四则运算工具。给模型一个算得准的算术通道——
 * 大模型的算术是语言模型「猜」出来的，让它心算多位数乘法，错得很自然也很隐蔽。
 *
 * <p>不接 ScriptEngine、不 eval、不解析 SpEL：
 * 一个「只是想让模型算个数」的工具，接了脚本引擎就变成任意代码执行。
 * 这里只做白名单 tokenizer 加递归下降求值，白名单外的字符一律拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MathTool implements AgentTool {

    /**
     * 入参长度上限，挡住超长表达式。
     */
    private static final int MAX_EXPRESSION_LENGTH = 200;

    /**
     * toolJson，工具结果的序列化与截断。
     */
    private final ToolJson toolJson;

    /**
     * 获取工具名。
     *
     * @return 工具名
     */
    @Override
    public String name() {
        return "math.calc";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "计算四则运算表达式，支持加减乘除、小数与括号，不支持函数与变量。"
                + "凡是涉及具体数字的计算都用它，不要心算；"
                + "统计、查询类的问题不属于这个工具。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"expression": {"type": "string",
                "description": "要计算的表达式，如 (12 + 8) * 3 / 2"}}, "required": ["expression"]}""";
    }

    /**
     * 获取危险等级。
     *
     * @return 危险等级
     */
    @Override
    public ToolRisk risk() {
        return ToolRisk.READ_ONLY;
    }

    /**
     * 计算表达式。
     *
     * @param arguments 入参，必填 expression
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String expression = ToolArguments.stringIn(arguments, "expression", "", MAX_EXPRESSION_LENGTH);
        if (expression.isEmpty()) {
            return ToolResult.fail("缺少 expression，请给出要计算的表达式", System.currentTimeMillis() - start);
        }
        try {
            double value = new Parser(expression).parse();
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("expression", expression);
            payload.put("result", trim(value));
            return ToolResult.ok(toolJson.write(payload), System.currentTimeMillis() - start);
        } catch (ArithmeticException e) {
            return ToolResult.fail("除数为零，请检查表达式", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool math.calc rejected expression={} reason={}", expression, e.getMessage());
            return ToolResult.fail("表达式无法计算：" + e.getMessage() + "，只支持数字、加减乘除与括号",
                    System.currentTimeMillis() - start);
        }
    }

    /**
     * 去掉浮点末尾多余的零，避免 3.0 被写成 3.0000000000000004。
     *
     * @param value 计算结果
     * @return 结果文本
     */
    private String trim(double value) {
        if (value == (long) value && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(Math.round(value * 1_000_000.0) / 1_000_000.0);
    }

    /**
     * 表达式解析器。递归下降，只认白名单字符，遇到其它字符直接抛异常。
     */
    private static final class Parser {

        /**
         * 表达式文本。
         */
        private final String text;

        /**
         * 当前位置。
         */
        private int position;

        /**
         * 构造解析器。
         *
         * @param text 表达式
         */
        private Parser(String text) {
            this.text = text;
        }

        /**
         * 解析并求值。
         *
         * @return 计算结果
         */
        private double parse() {
            double value = expression();
            skipWhitespace();
            if (position < text.length()) {
                throw new IllegalArgumentException("存在无法识别的字符");
            }
            return value;
        }

        /**
         * 解析加减。
         *
         * @return 值
         */
        private double expression() {
            double value = term();
            while (true) {
                skipWhitespace();
                if (position >= text.length()) {
                    return value;
                }
                char c = text.charAt(position);
                if (c == '+') {
                    position++;
                    value += term();
                } else if (c == '-') {
                    position++;
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        /**
         * 解析乘除。
         *
         * @return 值
         */
        private double term() {
            double value = factor();
            while (true) {
                skipWhitespace();
                if (position >= text.length()) {
                    return value;
                }
                char c = text.charAt(position);
                if (c == '*') {
                    position++;
                    value *= factor();
                } else if (c == '/') {
                    position++;
                    double divisor = factor();
                    if (divisor == 0.0) {
                        throw new ArithmeticException("divide by zero");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        /**
         * 解析数字、括号与一元负号。
         *
         * @return 值
         */
        private double factor() {
            skipWhitespace();
            if (position >= text.length()) {
                throw new IllegalArgumentException("表达式不完整");
            }
            char c = text.charAt(position);
            if (c == '-') {
                position++;
                return -factor();
            }
            if (c == '(') {
                position++;
                double value = expression();
                skipWhitespace();
                if (position >= text.length() || text.charAt(position) != ')') {
                    throw new IllegalArgumentException("括号不匹配");
                }
                position++;
                return value;
            }
            return number();
        }

        /**
         * 读取一个数字。
         *
         * @return 数字值
         */
        private double number() {
            int begin = position;
            boolean dot = false;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c >= '0' && c <= '9') {
                    position++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    position++;
                } else {
                    break;
                }
            }
            if (begin == position) {
                throw new IllegalArgumentException("这里应该是一个数字");
            }
            return Double.parseDouble(text.substring(begin, position));
        }

        /**
         * 跳过空白。
         */
        private void skipWhitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

    }

}
