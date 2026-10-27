package com.dong.rpc.controller;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.Result;
import com.dong.rpc.dto.ProductQueryResponse;
import com.dong.rpc.dto.RegistrySnapshot;
import com.dong.rpc.dto.RpcCallResult;
import com.dong.rpc.dto.RpcCompareRequest;
import com.dong.rpc.dto.RpcCompareResult;
import com.dong.rpc.dto.RpcEchoRequest;
import com.dong.rpc.dto.RpcProductRequest;
import com.dong.rpc.dto.RpcStatsResponse;
import com.dong.rpc.dto.RpcSumRequest;
import com.dong.rpc.service.RpcLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RPC 框架的验证入口。
 *
 * <p>这一层只做参数校验与转发，真正的结论在返回的数字里：
 * <ul>
 *   <li>{@code /echo} 与 {@code /echo/local} 是同一个实现的两条路径，比的是路径本身的代价</li>
 *   <li>{@code /sum} 用负数验证整型编码</li>
 *   <li>{@code /compare} 把远程与本地的耗时、protobuf 与 JSON 的体积放在一组数字里</li>
 *   <li>{@code /product} 是跨模块调用：实现在 search 模块，这里只有接口</li>
 * </ul>
 *
 * <p>接口按 dong.rpc.enabled 开关启停，没开时返回 1004。
 */
@RestController
@Validated
@RequestMapping("/api/rpc")
@RequiredArgsConstructor
@Tag(name = "RPC 框架")
public class RpcController {

    /**
     * labServiceProvider，对照实验服务提供者。
     */
    private final ObjectProvider<RpcLabService> labServiceProvider;

    /**
     * 远程调用回声服务，走完整 RPC 链路。
     */
    @PostMapping("/echo")
    @Operation(summary = "远程调用回声服务：编码 → TCP → 服务端反射调用 → 回程")
    public Result<RpcCallResult> echo(@Valid @RequestBody RpcEchoRequest request) {
        return Result.success(requireLabService().echoRemote(request.getText()));
    }

    /**
     * 本地直接调用同一个实现，作为对照组。
     */
    @PostMapping("/echo/local")
    @Operation(summary = "本地直接调用同一个实现，作为远程调用的对照组")
    public Result<RpcCallResult> echoLocal(@Valid @RequestBody RpcEchoRequest request) {
        return Result.success(requireLabService().echoLocal(request.getText()));
    }

    /**
     * 远程求和，用负数验证整型编码。
     */
    @PostMapping("/sum")
    @Operation(summary = "远程求和，默认两个负数，验证 zigzag 编码")
    public Result<RpcCallResult> sum(@Valid @RequestBody RpcSumRequest request) {
        return Result.success(requireLabService().sumRemote(request.getLeft(), request.getRight()));
    }

    /**
     * 对照实验。
     */
    @PostMapping("/compare")
    @Operation(summary = "远程 vs 本地耗时对照，以及 protobuf 与 JSON 的体积与编码耗时对比")
    public Result<RpcCompareResult> compare(@Valid @RequestBody RpcCompareRequest request) {
        return Result.success(requireLabService().compare(request.getText(), request.getTimes()));
    }

    /**
     * 注册表快照。
     */
    @PostMapping("/registry")
    @Operation(summary = "查看本进程暴露的服务，以及注册中心里能发现到的实例")
    public Result<RegistrySnapshot> registry() {
        return Result.success(requireLabService().registry());
    }

    /**
     * 运行状况。
     */
    @PostMapping("/stats")
    @Operation(summary = "客户端与服务端两侧的调用计数与平均耗时")
    public Result<RpcStatsResponse> stats() {
        return Result.success(requireLabService().stats());
    }

    /**
     * 跨模块调用。
     */
    @PostMapping("/product")
    @Operation(summary = "跨模块调用商品查询：实现在 search 模块，这里只依赖接口")
    public Result<ProductQueryResponse> product(@Valid @RequestBody RpcProductRequest request) {
        return Result.success(requireLabService().queryProduct(request.toQuery()));
    }

    /**
     * 取对照实验服务。
     *
     * @return 对照实验服务
     */
    private RpcLabService requireLabService() {
        RpcLabService service = labServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.rpc.enabled=true first");
        }
        return service;
    }

}
