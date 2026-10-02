package com.dong.agent.service.impl;

import com.dong.agent.dto.ToolDescriptor;
import com.dong.agent.dto.ToolDryRunRequest;
import com.dong.agent.dto.ToolDryRunResponse;
import com.dong.agent.service.AgentToolService;
import com.dong.agent.support.tool.AgentTool;
import com.dong.agent.support.tool.ToolRegistry;
import com.dong.agent.support.tool.ToolResult;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具服务实现。只暴露清单与只读工具的试运行。
 *
 * <p>有副作用的工具不接受试运行：试运行这个入口天然是给人手动点的，
 * 一旦放行有副作用的工具，「确认」这道防线就等于被绕过了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolServiceImpl implements AgentToolService {

    /**
     * toolRegistry，工具注册表。
     */
    private final ToolRegistry toolRegistry;

    /**
     * 查询工具清单。
     *
     * @return 工具描述列表
     */
    @Override
    public List<ToolDescriptor> list() {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        for (String name : toolRegistry.names()) {
            AgentTool tool = toolRegistry.get(name);
            if (tool == null) {
                continue;
            }
            ToolDescriptor descriptor = new ToolDescriptor();
            descriptor.setName(tool.name());
            descriptor.setDescription(tool.description());
            descriptor.setParametersSchema(tool.parametersSchema());
            descriptor.setRisk(tool.risk().name());
            descriptor.setNeedConfirm(tool.risk().needConfirm());
            descriptor.setTimeoutSeconds(tool.timeout().toSeconds());
            descriptors.add(descriptor);
        }
        return descriptors;
    }

    /**
     * 试运行只读工具。
     *
     * @param request 试运行请求
     * @return 执行结果
     */
    @Override
    public ToolDryRunResponse dryRun(ToolDryRunRequest request) {
        AgentTool tool = toolRegistry.get(request.getToolName());
        if (tool == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "tool not found " + request.getToolName());
        }
        if (tool.risk().needConfirm()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "tool has side effect, dry run is read-only tools only: " + request.getToolName());
        }
        Map<String, Object> arguments = request.getArguments() == null ? Map.of() : request.getArguments();
        ToolResult result;
        long start = System.currentTimeMillis();
        try {
            result = tool.invoke(arguments);
        } catch (Exception e) {
            log.warn("agent tool dry run threw name={}", request.getToolName(), e);
            result = ToolResult.fail("tool threw exception: " + e.getMessage(), System.currentTimeMillis() - start);
        }
        ToolDryRunResponse response = new ToolDryRunResponse();
        response.setSuccess(result.success());
        response.setPayload(result.payload());
        response.setErrorMessage(result.errorMessage());
        response.setElapsedMillis(result.elapsedMillis());
        return response;
    }

}
