package com.dong.agent.service;

import com.dong.agent.dto.LabResultQuery;
import com.dong.agent.dto.LabRunItem;
import com.dong.agent.dto.LabRunRequest;
import com.dong.agent.dto.LabRunResponse;
import com.dong.common.result.PageResult;

import java.util.List;

/**
 * 对照实验服务。
 *
 * <p>实验默认跑 mock 模型：真实模型有随机性，同一组对照两次跑出来数字不一样，
 * 混在一起对比会得出错误结论。真实模型只作为「偏差观察」。
 */
public interface AgentLabService {

    /**
     * 跑一组对照实验。
     *
     * @param experiment 实验编号，如 E2
     * @param request    请求
     * @return 各模式的对比结果
     */
    LabRunResponse run(String experiment, LabRunRequest request);

    /**
     * 查询历史实验结果。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<LabRunItem> results(LabResultQuery query);

    /**
     * 实验清单，页面据此渲染实验面板。
     *
     * @return 实验编号列表
     */
    List<String> experiments();

}
