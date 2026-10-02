package com.dong.agent.service;

import com.dong.agent.dto.ToolDescriptor;
import com.dong.agent.dto.ToolDryRunRequest;
import com.dong.agent.dto.ToolDryRunResponse;

import java.util.List;

/**
 * Agent 工具服务。只暴露清单与试运行，不提供任何新增或修改工具的入口——
 * 工具集是代码里注册好的，不能由接口动态添加，否则安全边界形同虚设。
 */
public interface AgentToolService {

    /**
     * 查询工具清单。
     *
     * @return 工具描述列表
     */
    List<ToolDescriptor> list();

    /**
     * 试运行只读工具。有副作用的工具不接受试运行。
     *
     * @param request 试运行请求
     * @return 执行结果
     */
    ToolDryRunResponse dryRun(ToolDryRunRequest request);

}
