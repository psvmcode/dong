package com.dong.agent.service.impl;

import com.dong.agent.dto.LabResultQuery;
import com.dong.agent.dto.LabRunItem;
import com.dong.agent.dto.LabRunRequest;
import com.dong.agent.dto.LabRunResponse;
import com.dong.agent.dto.RunRequest;
import com.dong.agent.dto.RunResponse;
import com.dong.agent.entity.AgentLabResult;
import com.dong.agent.mapper.AgentLabResultMapper;
import com.dong.agent.service.AgentLabService;
import com.dong.agent.service.AgentRunService;
import com.dong.agent.support.AgentRunOptions;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对照实验实现。八组实验覆盖工具注入、执行顺序、失败可见、上下文策略、
 * 自我校验、停止条件、幂等、并发隔离。
 *
 * <p>每组实验都只改一个变量：正常跑与实验跑走的是同一个 execute 入口，
 * 差异全在 AgentRunOptions 与历史窗口这两个入参上。
 * 这是对照实验成立的前提——否则比出来的是两套实现的差异，不是策略的差异。
 */
@Slf4j
@Service
public class AgentLabServiceImpl implements AgentLabService {

    /**
     * E4 连续对话轮数。必须让会话消息数明显超过默认窗口（20 条），
     * 否则窗口裁剪与全量历史拿到的是同一批消息，实验就看不出差别。
     */
    private static final int E4_ROUNDS = 6;

    /**
     * E4 全量历史模式下的窗口值，取一个大值即可，不需要真 unlimited。
     */
    private static final int E4_FULL_WINDOW = 1_000;

    /**
     * E6 无闸门模式的步数上限。不设成无上限是刻意的：
     * 真无上限要跑满墙钟 120 秒，代价太大，60 步已经足够说明「闸门在起作用」。
     */
    private static final int E6_LOOSE_STEPS = 60;

    /**
     * E8 并发数。要高于默认并发上限才能看出闸门生效，但也不能高太多：
     * 每条运行都要反复读写远程数据库，并发一高就把连接池打满，
     * 连其它模块的定时任务一起拖垮，实验反而跑不出结论。
     */
    private static final int E8_CONCURRENCY = 10;

    /**
     * runService，业务服务层。
     */
    private final AgentRunService runService;

    /**
     * labResultMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentLabResultMapper labResultMapper;

    /**
     * objectMapper，用于拼装实验特有指标。
     */
    private final ObjectMapper objectMapper;

    /**
     * 实验并发执行线程池。刻意用工具池而不是运行调度池：
     * 调度池大小等于并发上限，12 个任务提交进去会被排队，
     * 实际并发永远不会超过上限，闸门就永远不触发，实验也就白跑了。
     */
    private final Executor executor;

    /**
     * 模型提供方，记录在实验数据里。
     */
    @Value("${dong.agent.llm.provider:mock}")
    private String provider;

    /**
     * 构造实验服务。
     *
     * @param runService      运行服务
     * @param labResultMapper 实验结果 Mapper
     * @param objectMapper    JSON 序列化器
     * @param executor        并发执行线程池，用工具池
     */
    public AgentLabServiceImpl(AgentRunService runService, AgentLabResultMapper labResultMapper,
                               ObjectMapper objectMapper, @Qualifier("agentToolExecutor") Executor executor) {
        this.runService = runService;
        this.labResultMapper = labResultMapper;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    /**
     * 跑一组对照实验。
     *
     * @param experiment 实验编号
     * @param request    请求
     * @return 各模式的对比结果
     */
    @Override
    public LabRunResponse run(String experiment, LabRunRequest request) {
        String key = experiment == null ? "" : experiment.trim().toUpperCase();
        List<String> modes = request.getModes() == null || request.getModes().isEmpty()
                ? defaultModes(key) : request.getModes();
        int rounds = request.roundCount();
        LabRunResponse response = new LabRunResponse();
        response.setExperiment(key);
        response.setTitle(title(key));
        response.setMetrics(metrics(key));
        for (String mode : modes) {
            for (int round = 1; round <= rounds; round++) {
                LabRunItem item = runOnce(key, mode, round, request);
                response.getItems().add(item);
                save(item);
            }
        }
        return response;
    }

    /**
     * 查询历史实验结果。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<LabRunItem> results(LabResultQuery query) {
        PageRequest page = query.toPageRequest();
        String experiment = query.getExperiment();
        List<AgentLabResult> records = experiment == null || experiment.isBlank()
                ? labResultMapper.selectRecent(page.getPageSize())
                : labResultMapper.selectByExperiment(experiment.trim().toUpperCase(), page.getPageSize());
        List<LabRunItem> items = records.stream().map(this::toItem).toList();
        return PageResult.of(items, items.size(), page);
    }

    /**
     * 实验清单。
     *
     * @return 实验编号列表
     */
    @Override
    public List<String> experiments() {
        return List.of("E1", "E2", "E3", "E4", "E5", "E6", "E7", "E8");
    }

    /**
     * 跑一个模式一轮。
     *
     * @param key     实验编号
     * @param mode    模式
     * @param round   轮次
     * @param request 请求
     * @return 单项结果
     */
    private LabRunItem runOnce(String key, String mode, int round, LabRunRequest request) {
        String prompt = request.getPrompt() == null || request.getPrompt().isBlank()
                ? defaultPrompt(key) : request.getPrompt();
        try {
            return switch (key) {
                case "E1" -> runE1(mode, round, prompt);
                case "E2" -> runE2(mode, round, prompt);
                case "E3" -> runE3(mode, round, prompt);
                case "E4" -> runE4(mode, round, prompt);
                case "E5" -> runE5(mode, round, prompt);
                case "E6" -> runE6(mode, round, prompt);
                case "E7" -> runE7(mode, round, prompt);
                case "E8" -> runE8(mode, round, prompt);
                default -> throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown experiment " + key);
            };
        } catch (BusinessException e) {
            LabRunItem item = base(key, mode, round);
            item.setSuccess(false);
            item.setFinishReason("ERROR");
            item.setErrorMessage(e.getMessage());
            return item;
        }
    }

    /**
     * E1：工具注入方式。同样一句话，full 带上全部工具，topk 只带最相关的几个。
     */
    private LabRunItem runE1(String mode, int round, String prompt) {
        AgentRunOptions options = new AgentRunOptions(mode, null, null, null, null, null, null, null, null);
        RunResponse run = runService.runWithOptions(request(prompt, "e1", mode, round), options, null);
        LabRunItem item = base("E1", mode, round, run);
        item.setSuccess(run.getToolCalls() != null && run.getToolCalls() >= 1);
        item.setDetail(detail(Map.of("toolInject", mode)));
        return item;
    }

    /**
     * E2：执行顺序。mock 剧本对时间问题返回两个互不依赖的调用，
     * 串行是两次耗时相加，并行是取较慢的那次。
     */
    private LabRunItem runE2(String mode, int round, String prompt) {
        boolean parallel = "parallel".equals(mode);
        AgentRunOptions options = new AgentRunOptions(null, null, parallel, null, null, null, null, null, null);
        RunResponse run = runService.runWithOptions(request(prompt, "e2", mode, round), options, null);
        LabRunItem item = base("E2", mode, round, run);
        item.setSuccess(run.getToolCalls() != null && run.getToolCalls() >= 2);
        item.setDetail(detail(Map.of("parallel", parallel)));
        return item;
    }

    /**
     * E3：工具失败的处理。visible 把失败原文回传，swallow 把失败吞掉。
     * 判定标准是最终回答里有没有承认失败——吞掉失败的模型会一本正经地编答案。
     */
    private LabRunItem runE3(String mode, int round, String prompt) {
        AgentRunOptions options = new AgentRunOptions(null, mode, null, null, null, null, null, null, null);
        RunResponse run = runService.runWithOptions(request(prompt, "e3", mode, round), options, null);
        LabRunItem item = base("E3", mode, round, run);
        String answer = run.getAnswer() == null ? "" : run.getAnswer();
        boolean admitted = answer.contains("不存在") || answer.contains("失败") || answer.contains("无法");
        item.setSuccess("visible".equals(mode) ? admitted : !admitted);
        item.setDetail(detail(Map.of("failureMode", mode, "admittedFailure", admitted)));
        return item;
    }

    /**
     * E4：上下文策略。同一会话连续跑四轮，取最后一轮的提示 token 与耗时。
     * full 每轮都带上全部历史，window 只带窗口内的那部分。
     */
    private LabRunItem runE4(String mode, int round, String prompt) {
        boolean full = "full".equals(mode);
        Integer window = full ? E4_FULL_WINDOW : null;
        String sessionNo = null;
        RunResponse last = null;
        for (int i = 0; i < E4_ROUNDS; i++) {
            RunRequest request = request(prompt + "（第 " + (i + 1) + " 轮）", "e4", mode, round);
            request.setSessionNo(sessionNo);
            // 幂等键必须带时间戳：否则同一个实验跑第二次就会撞上第一次留下的记录
            request.setClientToken("lab-e4-" + mode + "-" + round + "-" + i + "-" + System.nanoTime());
            last = runService.runWithOptions(request, AgentRunOptions.empty(), window);
            sessionNo = last.getSessionNo();
        }
        LabRunItem item = base("E4", mode, round, last);
        item.setSuccess(last != null && last.getToolCalls() != null && last.getToolCalls() >= 1);
        item.setDetail(detail(Map.of("contextMode", mode, "rounds", E4_ROUNDS)));
        return item;
    }

    /**
     * E5：自我校验。verify 在模型给出回答后追加一轮复查，
     * 代价是多一次模型调用，收益是能挡住没有依据的结论。
     */
    private LabRunItem runE5(String mode, int round, String prompt) {
        boolean verify = "verify".equals(mode);
        AgentRunOptions options = new AgentRunOptions(null, null, null, verify, null, null, null, null, null);
        RunResponse run = runService.runWithOptions(request(prompt, "e5", mode, round), options, null);
        LabRunItem item = base("E5", mode, round, run);
        item.setSuccess("STOP".equals(run.getFinishReason()));
        item.setDetail(detail(Map.of("selfVerify", verify)));
        return item;
    }

    /**
     * E6：停止条件。摇摆剧本永远不会收敛，
     * 有闸门的在第 12 步被拦下，没闸门的要跑到 60 步才停。
     */
    private LabRunItem runE6(String mode, int round, String prompt) {
        boolean loose = "unbounded".equals(mode);
        Integer maxSteps = loose ? E6_LOOSE_STEPS : null;
        // 摇摆剧本会交替调用两个工具，不放宽重复检测的话，
        // 重复调用闸门会在第 7 步先拦下来，就轮不到步数闸门表现了
        Integer maxSame = loose ? E6_LOOSE_STEPS : null;
        AgentRunOptions options = new AgentRunOptions(null, null, null, null, maxSteps, null, null, null, maxSame);
        RunResponse run = runService.runWithOptions(request(prompt, "e6", mode, round), options, null);
        LabRunItem item = base("E6", mode, round, run);
        item.setSuccess(loose ? run.getSteps() > 12 : run.getSteps() <= 12);
        item.setDetail(detail(Map.of("stopCondition", mode)));
        return item;
    }

    /**
     * E7：幂等。同一个 clientToken 提交两次应当只产生一次运行；
     * 不带 token 则会老老实实跑两遍——页面断线重连就是这么烧掉钱的。
     */
    private LabRunItem runE7(String mode, int round, String prompt) {
        // 同一个实验内两次提交用同一个 token（这才是不带幂等键的对照组要验的东西），
        // 但每次跑实验要用新 token，否则第一次提交就撞上上次实验留下的记录
        String token = "lab-e7-" + mode + "-" + round + "-" + System.nanoTime();
        int executed = 0;
        int rejected = 0;
        RunResponse last = null;
        long start = System.currentTimeMillis();
        for (int i = 0; i < 2; i++) {
            RunRequest request = request(prompt, "e7", mode, round);
            request.setClientToken("token".equals(mode) ? token : token + "-" + i);
            try {
                last = runService.run(request);
                executed++;
            } catch (BusinessException e) {
                if (e.getCode() == Constants.CODE_IDEMPOTENT_REJECTED) {
                    rejected++;
                } else {
                    throw e;
                }
            }
        }
        LabRunItem item = base("E7", mode, round, last);
        item.setSteps(executed);
        item.setElapsedMillis((int) (System.currentTimeMillis() - start));
        item.setSuccess("token".equals(mode) ? executed == 1 && rejected == 1 : executed == 2);
        item.setDetail(detail(Map.of("executed", executed, "rejected", rejected)));
        return item;
    }

    /**
     * E8：并发隔离。并发数刻意高于默认上限：
     * bounded 会把超出的请求挡回去，shared 则全部放行——
     * 放行看起来更好，代价是 tomcat 线程被占满，全站一起变慢。
     */
    private LabRunItem runE8(String mode, int round, String prompt) {
        boolean bounded = "bounded".equals(mode);
        AtomicInteger done = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        long start = System.currentTimeMillis();
        // 闸门要等所有任务都到齐再一起放行，否则先跑完的会释放名额，
        // 后面的任务接着补位，并发上限看着生效其实没有
        CountDownLatch ready = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new ArrayList<>(E8_CONCURRENCY);
        for (int i = 0; i < E8_CONCURRENCY; i++) {
            int index = i;
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    ready.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                RunRequest request = request(prompt + "（并发 " + index + "）", "e8", mode, round);
                // 幂等键带时间戳，否则第二次跑同一个实验会撞上第一次留下的记录
                request.setClientToken("lab-e8-" + mode + "-" + round + "-" + index + "-" + System.nanoTime());
                try {
                    if (bounded) {
                        runService.run(request);
                    } else {
                        runService.runUngated(request);
                    }
                    done.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getCode() == Constants.CODE_TOO_MANY_REQUESTS) {
                        rejected.incrementAndGet();
                    } else {
                        log.warn("agent lab E8 run failed index={} code={}", index, e.getCode());
                    }
                }
            }, executor));
        }
        ready.countDown();
        for (CompletableFuture<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.warn("agent lab E8 task failed", e);
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        LabRunItem item = base("E8", mode, round);
        item.setSteps(E8_CONCURRENCY);
        item.setElapsedMillis((int) elapsed);
        item.setFinishReason(bounded ? "BOUNDED" : "SHARED");
        item.setSuccess(bounded ? rejected.get() > 0 : rejected.get() == 0);
        item.setDetail(detail(Map.of("concurrency", E8_CONCURRENCY, "done", done.get(), "rejected", rejected.get())));
        return item;
    }

    /**
     * 构造运行请求。
     *
     * @param prompt 输入
     * @param key    实验编号小写
     * @param mode   模式
     * @param round  轮次
     * @return 运行请求
     */
    private RunRequest request(String prompt, String key, String mode, int round) {
        RunRequest request = new RunRequest();
        request.setPrompt(prompt);
        request.setClientToken("lab-" + key + "-" + mode + "-" + round + "-" + System.nanoTime());
        return request;
    }

    /**
     * 构造结果骨架。
     *
     * @param experiment 实验编号
     * @param mode       模式
     * @param round      轮次
     * @return 结果项
     */
    private LabRunItem base(String experiment, String mode, int round) {
        LabRunItem item = new LabRunItem();
        item.setExperiment(experiment);
        item.setMode(mode);
        item.setRound(round);
        item.setProvider(provider);
        item.setSuccess(false);
        item.setSteps(0);
        item.setToolCalls(0);
        item.setPromptTokens(0);
        item.setCompletionTokens(0);
        item.setElapsedMillis(0);
        item.setDetail("{}");
        item.setErrorMessage("");
        return item;
    }

    /**
     * 用运行结果填充结果项。
     *
     * @param experiment 实验编号
     * @param mode       模式
     * @param round      轮次
     * @param run        运行结果
     * @return 结果项
     */
    private LabRunItem base(String experiment, String mode, int round, RunResponse run) {
        LabRunItem item = base(experiment, mode, round);
        if (run == null) {
            return item;
        }
        item.setRunNo(run.getRunNo());
        item.setFinishReason(run.getFinishReason());
        item.setSteps(run.getSteps() == null ? 0 : run.getSteps());
        item.setToolCalls(run.getToolCalls() == null ? 0 : run.getToolCalls());
        item.setPromptTokens(run.getPromptTokens() == null ? 0 : run.getPromptTokens());
        item.setCompletionTokens(run.getCompletionTokens() == null ? 0 : run.getCompletionTokens());
        item.setElapsedMillis(run.getElapsedMillis() == null ? 0 : run.getElapsedMillis());
        return item;
    }

    /**
     * 落库实验结果。
     *
     * @param item 结果项
     */
    private void save(LabRunItem item) {
        AgentLabResult record = new AgentLabResult();
        record.setExperiment(item.getExperiment());
        record.setMode(item.getMode());
        record.setRound(item.getRound());
        record.setProvider(item.getProvider());
        record.setSuccess(Boolean.TRUE.equals(item.getSuccess()) ? 1 : 0);
        record.setSteps(item.getSteps());
        record.setToolCalls(item.getToolCalls());
        record.setPromptTokens(item.getPromptTokens());
        record.setCompletionTokens(item.getCompletionTokens());
        record.setElapsedMillis(item.getElapsedMillis());
        record.setDetail(item.getDetail() == null ? "{}" : item.getDetail());
        labResultMapper.insert(record);
    }

    /**
     * 转换为结果项。
     *
     * @param record 实验记录
     * @return 结果项
     */
    private LabRunItem toItem(AgentLabResult record) {
        LabRunItem item = base(record.getExperiment(), record.getMode(), record.getRound());
        item.setProvider(record.getProvider());
        item.setSuccess(record.getSuccess() != null && record.getSuccess() == 1);
        item.setSteps(record.getSteps());
        item.setToolCalls(record.getToolCalls());
        item.setPromptTokens(record.getPromptTokens());
        item.setCompletionTokens(record.getCompletionTokens());
        item.setElapsedMillis(record.getElapsedMillis());
        item.setDetail(record.getDetail());
        return item;
    }

    /**
     * 把实验特有指标序列化成 JSON。
     *
     * @param values 指标
     * @return JSON 文本
     */
    private String detail(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(values));
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 实验默认模式。
     *
     * @param key 实验编号
     * @return 默认模式列表
     */
    private List<String> defaultModes(String key) {
        return switch (key) {
            case "E1" -> List.of("full", "topk");
            case "E2" -> List.of("serial", "parallel");
            case "E3" -> List.of("visible", "swallow");
            case "E4" -> List.of("window", "full");
            case "E5" -> List.of("none", "verify");
            case "E6" -> List.of("gated", "unbounded");
            case "E7" -> List.of("none", "token");
            case "E8" -> List.of("bounded", "shared");
            default -> List.of();
        };
    }

    /**
     * 实验默认输入。
     *
     * @param key 实验编号
     * @return 默认 prompt
     */
    private String defaultPrompt(String key) {
        return switch (key) {
            case "E1" -> "现在几点了";
            case "E2" -> "现在几点了";
            case "E3" -> "故意调用一个会失败的工具，看看失败怎么处理";
            case "E4" -> "现在几点了";
            case "E5" -> "现在几点了";
            case "E6" -> "摇摆";
            case "E7" -> "现在几点了";
            case "E8" -> "现在几点了";
            default -> "你好";
        };
    }

    /**
     * 实验标题。
     *
     * @param key 实验编号
     * @return 标题
     */
    private String title(String key) {
        return switch (key) {
            case "E1" -> "工具注入方式：全量注入 vs 按需注入";
            case "E2" -> "工具执行顺序：串行 vs 并行";
            case "E3" -> "工具失败处理：回传模型 vs 吞掉失败";
            case "E4" -> "上下文策略：窗口裁剪 vs 全量历史";
            case "E5" -> "自我校验：不校验 vs 追加复查";
            case "E6" -> "停止条件：五道闸门 vs 基本不设限";
            case "E7" -> "幂等：不带幂等键 vs 带幂等键";
            case "E8" -> "并发隔离：并发上限 vs 不设限";
            default -> "未知实验";
        };
    }

    /**
     * 观测指标说明。
     *
     * @param key 实验编号
     * @return 指标说明
     */
    private String metrics(String key) {
        return switch (key) {
            case "E1" -> "看 prompt token：工具描述是每轮都要带上车的固定成本";
            case "E2" -> "看耗时：互不依赖的多个调用，并行只等最慢的那个";
            case "E3" -> "看回答是否承认失败：吞掉失败会让模型把没查到当成查到了";
            case "E4" -> "看最后一轮的 prompt token 与耗时：历史越长越贵";
            case "E5" -> "看步数与生成 token：校验换来可靠性，代价是多一轮调用";
            case "E6" -> "看步数与耗时：没有闸门时模型会在两个思路之间来回摇摆";
            case "E7" -> "看实际执行次数：重复提交是烧钱最快的方式";
            case "E8" -> "看被拒数：挡回去的请求换来的是全站不被拖垮";
            default -> "";
        };
    }

}
