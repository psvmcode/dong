package com.dong.rpc.service.impl;

import com.dong.common.exception.BusinessException;
import com.dong.rpc.api.EchoService;
import com.dong.rpc.api.ProductQueryService;
import com.dong.rpc.codec.ProtoCodec;
import com.dong.rpc.dto.ProductItem;
import com.dong.rpc.dto.ProductQueryRequest;
import com.dong.rpc.dto.ProductQueryResponse;
import com.dong.rpc.dto.RpcCallResult;
import com.dong.rpc.dto.RpcCompareResult;
import com.dong.rpc.dto.RegistrySnapshot;
import com.dong.rpc.dto.RpcStatsResponse;
import com.dong.rpc.registry.LocalServiceProvider;
import com.dong.rpc.registry.ServiceDiscovery;
import com.dong.rpc.registry.ServiceInstance;
import com.dong.rpc.registry.impl.RedisServiceDiscovery;
import com.dong.rpc.service.RpcLabService;
import com.dong.rpc.support.RpcProxyFactory;
import com.dong.rpc.transport.RpcClient;
import com.dong.rpc.transport.RpcServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RPC 对照实验实现。
 *
 * <p>远程与本地调用的是<b>同一个实现类</b>：远程走动态代理 + TCP，本地直接调 Spring Bean。
 * 只有实现相同，「多出来的时间花在哪」这个问题才有答案。
 *
 * <p>序列化对比用同一份数据分别走 protobuf 与 Jackson，
 * 并且编码后再解码比对是否相等——体积小但解不回原值的对比没有意义。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.rpc", name = "enabled", havingValue = "true")
public class RpcLabServiceImpl implements RpcLabService {

    /**
     * 演示数据的条目数。
     */
    private static final int SAMPLE_ITEMS = 3;

    /**
     * proxyFactory，代理工厂。
     */
    private final RpcProxyFactory proxyFactory;

    /**
     * localEchoService，本地回声服务实现，对照组用。
     */
    private final EchoService localEchoService;

    /**
     * provider，本地服务表。
     */
    private final LocalServiceProvider provider;

    /**
     * discovery，服务发现。
     */
    private final ServiceDiscovery discovery;

    /**
     * redisDiscovery，服务发现的具体实现，用来取本地缓存的命中情况。
     */
    private final RedisServiceDiscovery redisDiscovery;

    /**
     * client，RPC 客户端。
     */
    private final RpcClient client;

    /**
     * server，RPC 服务端。
     */
    private final RpcServer server;

    /**
     * objectMapper，JSON 序列化器，用于对比。
     */
    private final ObjectMapper objectMapper;

    /**
     * 远程回声服务代理，懒创建。
     */
    private volatile EchoService remoteEchoService;

    /**
     * 远程商品查询代理，懒创建。
     */
    private volatile ProductQueryService remoteProductService;

    @Override
    public RpcCallResult echoRemote(String text) {
        long started = System.nanoTime();
        try {
            String result = remoteEcho().echo(text);
            return result(true, result, elapsedMillis(started), null);
        } catch (BusinessException ex) {
            return result(false, null, elapsedMillis(started), "code=" + ex.getCode() + " " + ex.getMessage());
        }
    }

    @Override
    public RpcCallResult echoLocal(String text) {
        long started = System.nanoTime();
        try {
            String result = localEchoService.echo(text);
            return result(true, result, elapsedMillis(started), null);
        } catch (BusinessException ex) {
            return result(false, null, elapsedMillis(started), "code=" + ex.getCode() + " " + ex.getMessage());
        }
    }

    @Override
    public RpcCallResult sumRemote(int left, int right) {
        long started = System.nanoTime();
        try {
            int value = remoteEcho().sum(left, right);
            return result(true, String.valueOf(value), elapsedMillis(started), null);
        } catch (BusinessException ex) {
            return result(false, null, elapsedMillis(started), "code=" + ex.getCode() + " " + ex.getMessage());
        }
    }

    @Override
    public RpcCompareResult compare(String text, int times) {
        RpcCompareResult result = new RpcCompareResult();
        result.setTimes(times);
        long started = System.nanoTime();
        for (int index = 0; index < times; index++) {
            remoteEcho().echo(text);
        }
        double remoteMillis = elapsedMillis(started);
        started = System.nanoTime();
        for (int index = 0; index < times; index++) {
            localEchoService.echo(text);
        }
        double localMillis = elapsedMillis(started);
        result.setRemoteTotalMs(remoteMillis);
        result.setLocalTotalMs(localMillis);
        result.setRemoteAvgMs(remoteMillis / times);
        result.setLocalAvgMs(localMillis / times);
        result.setRemoteToLocalRatio(localMillis == 0 ? 0 : remoteMillis / localMillis);
        compareSerialization(result);
        log.info("rpc compare: remote={}ms local={}ms proto={}B json={}B", remoteMillis, localMillis, result.getProtoBytes(), result.getJsonBytes());
        return result;
    }

    @Override
    public RegistrySnapshot registry() {
        RegistrySnapshot snapshot = new RegistrySnapshot();
        List<String> exposed = new ArrayList<>(provider.serviceNames());
        Map<String, List<String>> discovered = new LinkedHashMap<>();
        for (String serviceName : exposed) {
            List<String> addresses = new ArrayList<>();
            for (ServiceInstance instance : discovery.discover(serviceName)) {
                addresses.add(instance.address());
            }
            discovered.put(serviceName, addresses);
        }
        snapshot.setExposedServices(exposed);
        snapshot.setDiscovered(discovered);
        return snapshot;
    }

    @Override
    public RpcStatsResponse stats() {
        RpcStatsResponse response = new RpcStatsResponse();
        response.setServerRunning(server.isRunning());
        response.setServerPort(server.getPort());
        response.setServerInvocations(provider.invocations());
        response.setServerFailures(provider.failures());
        response.setClientRequests(client.requests());
        response.setClientFailures(client.failures());
        response.setClientAverageElapsedMs(client.averageElapsed());
        response.setDiscoveryCacheHits(redisDiscovery.cacheHits());
        response.setDiscoveryCacheMisses(redisDiscovery.cacheMisses());
        return response;
    }

    @Override
    public ProductQueryResponse queryProduct(ProductQueryRequest request) {
        return remoteProduct().query(request);
    }

    /**
     * protobuf 与 JSON 的序列化对比。
     *
     * @param result 对照结果
     */
    private void compareSerialization(RpcCompareResult result) {
        ProductQueryResponse sample = sampleResponse();
        long started = System.nanoTime();
        byte[] proto = ProtoCodec.encode(sample);
        result.setProtoEncodeMs(elapsedMillis(started));
        try {
            started = System.nanoTime();
            byte[] json = objectMapper.writeValueAsBytes(sample);
            result.setJsonEncodeMs(elapsedMillis(started));
            result.setJsonBytes(json.length);
        } catch (Exception ex) {
            log.warn("json encode failed: {}", ex.getMessage());
            result.setJsonBytes(0);
            result.setJsonEncodeMs(0);
        }
        result.setProtoBytes(proto.length);
        result.setRoundTripEqual(sample.equals(ProtoCodec.decode(proto, ProductQueryResponse.class)));
        result.setJsonToProtoRatio(proto.length == 0 ? 0 : (double) result.getJsonBytes() / proto.length);
    }

    /**
     * 演示数据。带嵌套消息与重复字段，序列化对比才有代表性。
     *
     * @return 演示响应
     */
    private ProductQueryResponse sampleResponse() {
        ProductQueryResponse response = new ProductQueryResponse();
        response.setTotal(SAMPLE_ITEMS);
        List<ProductItem> items = new ArrayList<>(SAMPLE_ITEMS);
        for (int index = 1; index <= SAMPLE_ITEMS; index++) {
            ProductItem item = new ProductItem();
            item.setId(String.valueOf(index));
            item.setName("演示商品 " + index);
            item.setCategory("demo");
            item.setPrice(19.9D * index);
            items.add(item);
        }
        response.setItems(items);
        return response;
    }

    /**
     * 取远程回声服务代理，只创建一次。
     *
     * @return 代理
     */
    private EchoService remoteEcho() {
        EchoService service = remoteEchoService;
        if (service == null) {
            synchronized (this) {
                service = remoteEchoService;
                if (service == null) {
                    service = proxyFactory.create(EchoService.class);
                    remoteEchoService = service;
                }
            }
        }
        return service;
    }

    /**
     * 取远程商品查询代理，只创建一次。
     *
     * @return 代理
     */
    private ProductQueryService remoteProduct() {
        ProductQueryService service = remoteProductService;
        if (service == null) {
            synchronized (this) {
                service = remoteProductService;
                if (service == null) {
                    service = proxyFactory.create(ProductQueryService.class);
                    remoteProductService = service;
                }
            }
        }
        return service;
    }

    /**
     * 组装调用结果。
     *
     * @param ok      是否成功
     * @param value   返回值
     * @param elapsed 耗时
     * @param error   失败原因
     * @return 调用结果
     */
    private RpcCallResult result(boolean ok, String value, double elapsed, String error) {
        RpcCallResult result = new RpcCallResult();
        result.setOk(ok);
        result.setResult(value);
        result.setElapsedMs(elapsed);
        result.setError(error);
        return result;
    }

    /**
     * 耗时，毫秒。
     *
     * @param startedNanos 起始纳秒
     * @return 毫秒
     */
    private double elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0D;
    }

}
