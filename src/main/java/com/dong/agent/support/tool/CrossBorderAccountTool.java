package com.dong.agent.support.tool;

import com.dong.agent.enums.ToolRisk;
import com.dong.crossborder.service.CrossBorderAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 跨境账户查询工具。只读余额与额度，不做任何资金操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrossBorderAccountTool implements AgentTool {

    /**
     * accountService，业务服务层。
     */
    private final CrossBorderAccountService accountService;

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
        return "crossborder.account";
    }

    /**
     * 获取工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String description() {
        return "按账号查询跨境账户，返回余额、冻结金额、可用余额与日限额、单笔限额。"
                + "当用户问某个跨境账户有多少钱、额度是多少时调用；"
                + "涉及汇款、冻结、解冻的操作一律不做。";
    }

    /**
     * 获取入参的 JSON Schema。
     *
     * @return JSON Schema 字符串
     */
    @Override
    public String parametersSchema() {
        return """
                {"type": "object", "properties": {"accountNo": {"type": "string",
                "description": "跨境账户账号"}}, "required": ["accountNo"]}""";
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
     * 查询账户。
     *
     * @param arguments 入参，必填 accountNo
     * @return 执行结果
     */
    @Override
    public ToolResult invoke(Map<String, Object> arguments) {
        long start = System.currentTimeMillis();
        String accountNo = ToolArguments.stringIn(arguments, "accountNo", "", 64);
        if (accountNo.isEmpty()) {
            return ToolResult.fail("缺少 accountNo，请给出跨境账户账号", System.currentTimeMillis() - start);
        }
        try {
            Object account = accountService.findByAccountNo(accountNo);
            if (account == null) {
                return ToolResult.fail("账户不存在：" + accountNo, System.currentTimeMillis() - start);
            }
            return ToolResult.ok(toolJson.write(account), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("agent tool crossborder.account failed accountNo={}", accountNo, e);
            return ToolResult.fail("查询账户失败：" + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

}
