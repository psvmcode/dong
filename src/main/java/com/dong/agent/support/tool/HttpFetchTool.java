package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.common.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 抓取公开网页内容的工具。
 *
 * <p>这是工具层最容易被打穿的一项：能发任意 HTTP 请求，
 * 等于把服务器在内网的位置暴露给了模型（以及任何能影响模型输入的人）。
 * 因此默认关闭，要打开必须显式配置。
 *
 * <p>防护按顺序做：协议白名单 → 解析后逐个 IP 判定内网 → 禁止重定向 → 端口白名单 → 大小与超时。
 * 需要说明的局限：DNS rebinding 挡不住（判定时解析到公网、请求时解析到内网），
 * 完整防护要定制 HTTP 客户端，本项目不追求挡住所有攻击，靠默认关闭把风险面降到零。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.agent.tools", name = "http-fetch-enabled", havingValue = "true")
public class HttpFetchTool implements AgentTool {

    /**
     * 连接超时。
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 请求超时。
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 允许的协议。
     */
    private static final Map<String, Integer> ALLOWED_PORTS = Map.of("http", 80, "https", 443);

    /**
     * 结果字符上限。
     */
    private static final int MAX_CHARS = 8_000;

    /**
     * 单工具超时，抓取比一般工具慢，给到 15 秒。
     */
    private final Duration timeout = Duration.ofSeconds(15);

    /**
     * HTTP 客户端，不跟随重定向。
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * toolJson，工具结果的序列化与截断。
     */
    private final ToolJson toolJson;

    /**
     * 是否允许抓取，默认关闭。
     */
    @Value("${dong.agent.tools.http-fetch-enabled:false}")
    private boolean enabled;

    /**
     * 获取工具名。
     *
     * @return 工具名
     */
    @Override
    public String name() {
        return "http.fetch";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "抓取一个公开网页的正文内容，只能访问公网的 http/https 地址。"
                + "当用户给了具体网址、或需要查证外部资料时调用；"
                + "内网地址、云元数据地址一律拒绝，不要反复尝试。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"url": {"type": "string",
                "description": "完整的网页地址，必须以 http:// 或 https:// 开头"}}, "required": ["url"]}""";
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
     * 抓取网页。
     *
     * @param arguments 入参，必填 url
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        if (!enabled) {
            return ToolResult.fail("http.fetch 未启用，需要设置 dong.agent.tools.http-fetch-enabled=true",
                    System.currentTimeMillis() - start);
        }
        String url = ToolArguments.stringIn(arguments, "url", "", Constants.MAX_TEXT_LENGTH);
        if (url.isEmpty()) {
            return ToolResult.fail("缺少 url，请给出完整的网页地址", System.currentTimeMillis() - start);
        }
        URI uri;
        try {
            uri = URI.create(url.trim()).normalize();
        } catch (Exception e) {
            return ToolResult.fail("地址格式不正确：" + url, System.currentTimeMillis() - start);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!ALLOWED_PORTS.containsKey(scheme)) {
            return ToolResult.fail("只允许 http 与 https 协议，收到的是 " + scheme, System.currentTimeMillis() - start);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return ToolResult.fail("地址里没有主机名", System.currentTimeMillis() - start);
        }
        int port = uri.getPort() == -1 ? ALLOWED_PORTS.get(scheme) : uri.getPort();
        if (port != 80 && port != 443) {
            return ToolResult.fail("只允许 80 与 443 端口，收到的是 " + port, System.currentTimeMillis() - start);
        }
        String blocked = checkHost(host);
        if (blocked != null) {
            log.warn("agent tool http.fetch blocked host={} reason={}", host, blocked);
            return ToolResult.fail("该地址不允许访问：" + blocked, System.currentTimeMillis() - start);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("User-Agent", "dong-agent/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200) {
                return ToolResult.fail("目标返回状态码 " + status, System.currentTimeMillis() - start);
            }
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("url", uri.toString());
            payload.put("status", String.valueOf(status));
            payload.put("content", readText(response.body()));
            return ToolResult.ok(toolJson.write(payload), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool http.fetch failed url={}", url, e);
            return ToolResult.fail("抓取失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    /**
     * 获取超时。
     *
     * @return 超时时长
     */
    @Override
    public Duration timeout() {
        return timeout;
    }

    /**
     * 判定主机是否指向内网。解析出的每一个地址都要判，
     * 只判第一个会漏掉「一个公网地址加一个内网地址」的域名。
     *
     * @param host 主机名
     * @return 不允许时返回原因，允许时返回 null
     */
    private String checkHost(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            return "无法解析该主机名";
        }
        if (addresses == null || addresses.length == 0) {
            return "无法解析该主机名";
        }
        for (InetAddress address : addresses) {
            if (address.isLoopbackAddress()) {
                return "回环地址不允许访问";
            }
            if (address.isLinkLocalAddress()) {
                return "链路本地地址不允许访问（含云元数据地址）";
            }
            if (address.isSiteLocalAddress()) {
                return "私有网段不允许访问";
            }
            if (address.isMulticastAddress() || address.isAnyLocalAddress()) {
                return "组播或通配地址不允许访问";
            }
        }
        return null;
    }

    /**
     * 读取响应正文并截断。
     *
     * @param in 响应流
     * @return 正文文本
     */
    private String readText(InputStream in) {
        StringBuilder text = new StringBuilder();
        try (InputStream stream = in;
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) > 0 && text.length() < MAX_CHARS) {
                text.append(buffer, 0, length);
            }
        } catch (Exception e) {
            log.warn("agent tool http.fetch read failed", e);
        }
        if (text.length() > MAX_CHARS) {
            return text.substring(0, MAX_CHARS) + "...(内容已截断)";
        }
        return Arrays.stream(text.toString().split("\n"))
                .filter(line -> !line.isBlank())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }

}
