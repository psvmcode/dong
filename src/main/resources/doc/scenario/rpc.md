# RPC 框架模块说明

`com.dong.rpc`，接口前缀 `/api/rpc`。

一个能跑通的极简 RPC 轮子：**零新增依赖自研 protobuf 编解码** + **Netty 长连接** + **Redis 注册发现**。
目标不是替代 Dubbo / gRPC，而是把「一次跨服务调用到底发生了什么」拆开摆出来：
编码、寻址、连接、反射调用、回程解码，每一步的代价都能在接口返回的数字里看到。

---

## 一、模块概览

```
调用方 echoService.echo("hi")
        │
        ▼  JDK 动态代理
RpcInvocationHandler
        │  1 服务发现（本地缓存 → Redis ZSet）
        │  2 参数 protobuf 编码
        ▼
RpcClient ──[4 字节长度 + protobuf 报文]──► RpcServer（独立端口 9090）
        │                                        │ 3 反射调用本地实现
        │  5 响应解码                             │ 4 返回值编码
        ▼                                        ▼
     返回值                              LocalServiceProvider
```

| 包 | 职责 |
|---|---|
| `annotation` | `@ProtoMessage` / `@ProtoField` / `@RpcService`，schema 与服务的声明方式 |
| `codec` | protobuf 编解码：`ProtoWriter` / `ProtoReader` / `ProtoSchema` / `ProtoCodec` / `Varints` |
| `protocol` | `RpcRequest` / `RpcResponse` / `RpcStatus`，线上传输的三个消息 |
| `transport` | Netty 服务端、客户端与两个 handler |
| `registry` | 注册与发现接口、Redis 实现、本地服务表 |
| `support` | 服务暴露器、代理工厂与调用逻辑 |
| `task` | 注册中心心跳 |
| `api` / `dto` | 对外接口与消息：任何模块只要依赖它们就能调用别人 |

**依赖方向是刻意设计的**：接口与注解都在 rpc 模块里，业务模块自己写实现并打上 `@RpcService`。
于是编译期依赖是单向的（业务 → 基础设施），rpc 不引用任何业务模块——
否则基础设施一旦引用业务，就再也没法被第二个业务模块复用。

---

## 二、协议：自研 protobuf wire format

### 2.1 编码规则

| 部件 | 规则 |
|---|---|
| tag | `序号 << 3 \| 线类型`，varint |
| varint | 每字节低 7 位有效，最高位是「还有后续」标志，小端 |
| 整型 | **zigzag + varint**，`-1` 只要 1 个字节 |
| 字符串 / 字节 / 嵌套消息 | varint 长度 + 内容 |
| double / float | 定长 8 / 4 字节，**小端** |

### 2.2 与官方 protobuf 的异同

| 点 | 本实现 | 官方 |
|---|---|---|
| varint / TLV / 线类型 | 一致 | 一致 |
| 未知字段 | 跳过（前向兼容） | 一致 |
| null 与默认值 | 不编码 | proto3 一致 |
| 整型 | **统一 zigzag**（等价 sint32/sint64） | int32 负数固定 10 字节 |
| group | 不支持 | 已废弃 |

整型统一走 zigzag 是刻意选择：RPC 里负数并不少见（错误码、差值、时间偏移），
官方 int32 编码负数要 10 个字节，zigzag 只要 1 个。
**代价是它与 protoc 生成的 int32 字段不互通**——真要和外面对接，把整数字段声明成 sint32 即可对齐。

### 2.3 schema 由注解描述

```java
@Data
@ProtoMessage
public class ProductQueryResponse {

    @ProtoField(order = 1, type = ProtoType.INT64)
    private long total;

    @ProtoField(order = 2, type = ProtoType.MESSAGE, repeated = true)
    private List<ProductItem> items;

}
```

序号与类型都必须显式写。序号不显式就依赖字段声明顺序，而字段顺序会被 IDE 随手改动；
类型交给推断，等于把线上格式交给一次重构。schema 首次使用时反射一次并缓存——
RPC 是高频路径，每条消息都反射一遍会盖过编解码本身的开销。

---

## 三、传输：Netty 与粘包拆包

帧格式是 **4 字节长度 + protobuf 报文**，靠 `LengthFieldBasedFrameDecoder` / `LengthFieldPrepender` 切帧。

- **长度前缀不是可选项**：TCP 是字节流，没有消息边界，一次 write 可能被拆成多次 read，
  两次 write 也可能被合并成一次 read。不切帧，业务拿到的就是半条消息。
- **最大帧长必须设上限**（默认 10MB）：不设的话对端只要不停发数据，服务端就会一直攒内存直到 OOM。
- **一条地址一条长连接**：TCP 握手是毫秒级，而调用本身可能只有几百微秒，不复用连接握手会比调用还贵。
  取用前检查 `isActive`，断了就重连。
- **requestId 是必需的**：单条连接上并发跑着很多请求，响应乱序回来，没有 id 就只能串行调用。
- **超时是必需的**：没有超时的同步调用会一直占着线程，对端一挂就是一堆永远醒不过来的请求。

---

## 四、注册与发现

### 4.1 存储结构：Redis ZSet

```
key   = lab:rpc:svc:{serviceName}
member= "host:port"
score = 最后一次心跳的毫秒时间戳
```

为什么是 ZSet 而不是 Set 或 String+TTL：

| 方案 | 问题 |
|---|---|
| Set | 成员没有独立过期时间，被 kill -9 的实例永远留在里面 |
| String + TTL | 每个实例一个 key，发现时要 KEYS 再逐个查（KEYS 生产禁用） |
| **ZSet** | score 天然是心跳时间；发现=按 score 取区间，清理=删一个区间 |

### 4.2 心跳续约

注册不是一次性的：进程被 kill -9 时没机会执行注销，注册中心会留下永远存在却永远连不上的地址。
带 TTL 的续约把「实例已死」变成「最多错一个 TTL」（默认 30 秒续约一次、TTL 30 秒/缓存 5 秒）。

### 4.3 本地缓存与降级

- **服务列表本地缓存 5 秒**：注册中心的一次往返往往比调用本身还贵。
  实测从云服务器上的 Redis 取一次列表要 30 毫秒，而调用本身不到 1 毫秒——
  没有缓存时，「找地址」能占掉调用耗时的九成以上。
- **空结果不缓存**：服务还没起来时把「没有提供者」缓存住，
  会让刚上线的服务在缓存过期前一直不可用，这是最难排查的一类故障。
- **Redis 不可用降级到配置的地址**：中间件故障不该被放大成业务不可用。
  单机时兜底地址与注册地址一致，于是 Redis 挂了只是多一条 warn，调用照常。

---

## 五、调用链

```java
EchoService remote = rpcProxyFactory.create(EchoService.class);
remote.echo("hi");     // 看起来是本地调用，实际是一次 TCP 往返
```

1. 动态代理拦下调用，按接口名作为服务名去发现实例，随机挑一个（负载均衡）。
2. 参数逐个编码成字节，连同参数类型名一起装进 `RpcRequest`。
3. 客户端写出帧，挂起等待（requestId → CompletableFuture）。
4. 服务端解帧、解码请求、在本地服务表里按「方法名 + 参数类型」匹配方法、反射调用。
5. 返回值编码后回写，客户端解码成接口方法的返回类型。

失败一律翻译成状态而不是抛服务端异常类型：客户端不一定有那个异常类，
传过去只会换来一个反序列化失败，把真正的错误信息盖掉。

---

## 六、接口文档

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/rpc/echo` | 远程调用回声服务，走完整链路 |
| POST | `/api/rpc/echo/local` | 本地直调同一个实现，对照组 |
| POST | `/api/rpc/sum` | 远程求和，默认两个负数，验证 zigzag |
| POST | `/api/rpc/compare` | 远程 vs 本地耗时，以及 protobuf vs JSON 的体积与编码耗时 |
| POST | `/api/rpc/registry` | 本进程暴露的服务 + 注册中心能发现到的实例 |
| POST | `/api/rpc/stats` | 客户端与服务端计数、平均耗时、发现缓存命中 |
| POST | `/api/rpc/product` | 跨模块调用：实现在 search 模块，这里只有接口 |

错误码：`1004` 模块未启用；`1005` 没有可用提供者 / 超时 / 服务端报错 / 方法不存在。

---

## 七、数据模型

本模块不建表。Redis 里的 key 只有一类：`lab:rpc:svc:{serviceName}`（ZSet）。

跨模块示例的两个接口都在 `com.dong.rpc.api`：

| 接口 | 实现位置 | 说明 |
|---|---|---|
| `EchoService` | `com.dong.rpc.demo.EchoServiceImpl` | rpc 自带，不依赖任何中间件 |
| `ProductQueryService` | `com.dong.search.rpc.ProductSearchRpcService` | 走 ES 检索，按 ES 开关启停 |

---

## 八、本地验证

```bash
mvn spring-boot:run

# 1. 远程调用（第一次含建连与注册中心查询，之后走本地缓存）
curl -X POST http://127.0.0.1:8090/api/rpc/echo -H 'Content-Type: application/json' -d '{"text":"hi"}'

# 2. 负数求和，验证 zigzag
curl -X POST http://127.0.0.1:8090/api/rpc/sum -H 'Content-Type: application/json' -d '{"left":-128,"right":-256}'

# 3. 对照实验
curl -X POST http://127.0.0.1:8090/api/rpc/compare -H 'Content-Type: application/json' -d '{"text":"hi","times":50}'

# 4. 跨模块调用
curl -X POST http://127.0.0.1:8090/api/rpc/product -H 'Content-Type: application/json' -d '{"keyword":"手机","pageSize":2}'

# 5. 注册与运行状况
curl -X POST http://127.0.0.1:8090/api/rpc/registry -H 'Content-Type: application/json' -d '{}'
curl -X POST http://127.0.0.1:8090/api/rpc/stats   -H 'Content-Type: application/json' -d '{}'
```

开关：`dong.rpc.enabled`（默认 true），关掉后不监听端口、不注册服务，接口返回 `1004`。
关键参数：`port`、`register-host`、`default-addresses`、`heartbeat-interval-ms`、
`instance-ttl-ms`、`discovery-cache-ms`、`call-timeout-ms`、`max-frame-length`。

### 本次实测（2026-10-27）

| 指标 | 数字 |
|---|---|
| 首次远程调用 | 34.4ms（含注册中心查询 + 建连） |
| 带缓存后平均远程调用（50 次） | 0.29ms/次，总 14.6ms |
| 本地直接调用（50 次） | 0.00006ms/次 |
| 发现缓存 | 命中 50 / 未命中 1 |
| 同一份数据 protobuf vs JSON | 110B vs 233B（2.1 倍） |
| 解码回原值 | 相等（roundTripEqual=true） |
| 负数求和 | -384（zigzag 编码正确） |
| 跨模块调用 | 命中 73 条商品，返回 search 模块的数据 |

协议层另有一轮离线自检：zigzag 边界值（含 Long.MIN_VALUE）、未知字段跳过、
嵌套消息与浮点往返，全部通过。

---

## 九、没做的部分（有意的边界）

这个轮子只做到「能跑通 + 关键取舍可见」，以下都是真实框架必需、但本模块刻意没做的：

- **重试与熔断**：调用失败直接返回状态，不重试。重试要解决幂等，否则会把一次写操作放大成 N 次。
- **连接池**：一条地址一条连接，没有按并发建多条。
- **异步调用**：客户端内部用 CompletableFuture 挂起，但对外只暴露同步接口。
- **优雅的负载均衡**：随机，不带权重、不带最少连接。
- **压缩**：大报文没有 snappy / gzip。
- **泛化调用**：参数传的是字节，服务端按自己的类型解码，但没提供不带接口类的调用入口。

这些缺口的共同点是：**它们都是在「能调通」之后才需要的东西**，
而它们的代价（幂等、状态、可观测性）恰恰是这个模块想让人看见的部分。
