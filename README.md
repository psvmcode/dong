# dong-lab

中间件与分布式场景实验室。一个 Spring Boot 3 工程，把日常开发中容易只在理论上理解的中间件用法，全部变成可以运行、可以对比、可以用数据验证的代码。

技术栈：Spring Boot 3.4.5 / JDK 21 / MySQL 8 / Redis 7 / Redisson 3.45 / MyBatis（原生 XML）/ RocketMQ / Kafka / Elasticsearch 8.15 / MongoDB 7 / MariaDB 10.11

---

## 一、这个项目的定位

它不是脚手架，也不是业务系统，而是一组**可对照实验**。每个场景都回答三个问题：

1. 没有这个中间件时，问题长什么样（错误现象 + 量化损失）
2. 用了之后，为什么能解决（原理 + 关键代码）
3. 代价是什么（性能、一致性、运维复杂度）

例如分布式锁，项目同时提供了「不加锁」和「加 Redisson 锁」两个接口。实测在 8 线程 × 10 次并发自增下：

| 模式 | 期望值 | 实际值 | 丢失更新 | 耗时 |
|---|---|---|---|---|
| 不加锁 | 80 | 2 | 78 | 280ms |
| Redisson 锁 | 80 | 80 | 0 | 18.4s |

数字本身就在说明：锁换来正确性，代价是 65 倍的耗时。这类对照在项目中一共有三十余处。

---

## 二、目录结构

顶层按**限界上下文**划分（DDD 的边界），每个上下文内部按**传统分层**组织（controller / service + impl / mapper / entity / dto / enums）。这样既保住了场景之间的边界，又符合后端团队的阅读习惯。

```
src/main/java/com/dong/lab/
├── common/                     共享内核，不含业务逻辑
│   ├── constant/Constants      错误码与固定值
│   ├── result/                 Result、PageRequest、PageResult
│   ├── exception/              BusinessException、GlobalExceptionHandler
│   └── util/                   JsonUtils、Base62Utils、Snowflake
│
├── config/                     全局装配：数据源、Redisson、Jackson、线程池、OpenAPI
│
├── framework/                  技术能力层，与业务无关，可被任意上下文复用
│   ├── redis/RedisService      Redis 门面，脚本参数统一转字符串
│   ├── lock/                   Redisson 分布式锁 + LockHandle（支持 try-with-resources）
│   ├── cache/                  多级缓存（L1 Caffeine + L2 Redis）、失效总线、统计
│   ├── limiter/                固定窗口 / 滑动窗口 / 令牌桶 / 漏桶 + @RateLimited 切面
│   ├── bloom/                  Redisson 布隆过滤器
│   └── mq/                     消息抽象：LocalMessageBus / RocketMQ / Kafka + MqFacade 路由
│
├── cache/                      多级缓存：穿透、击穿、雪崩、双写一致性
├── classic/                    Redis 经典场景：排行榜、UV、签到、短链、GEO、延迟队列、发号器
├── seckill/                    秒杀：预扣库存、Lua 原子脚本、异步下单、售罄短路
├── redpacket/                  抢红包：二倍均值法、预分配、原子抢
├── social/                     微博模型：关注关系、共同关注、推拉两种时间线
├── search/                     Elasticsearch：IK 分词、高亮、过滤、聚合
├── tcc/                        分布式事务 TCC：Try/Confirm/Cancel、恢复任务
├── mq/                         消息场景：顺序、延迟、幂等、死信
├── doc/                        MongoDB：无 schema 日志
└── replica/                    MariaDB：第二数据源、独立事务管理器

src/main/resources/
├── application.yml             本地配置，全部中间件默认关闭
├── application-remote.yml      云服务器配置
└── mapper/**/*.xml             手写 SQL

db/schema.sql                   建表语句
deploy/                         Docker Compose 编排与安装脚本
```

**分层约定**

| 目录 | 职责 | 禁止 |
|---|---|---|
| `controller` | 参数校验、调 service、封装 Result | 写业务逻辑 |
| `service` | 接口定义 | — |
| `service/impl` | 业务逻辑、事务边界 | 直接依赖具体中间件实现 |
| `mapper` | 数据访问 | 写业务逻辑 |
| `entity` | 与表一一对应 | 放业务方法 |
| `dto` | 请求 / 响应对象 | 与 entity 混用 |
| `enums` | 状态码，落库为 int | 用魔法数字 |

---

## 三、快速开始

### 1. 准备数据库

```bash
mysql -uroot -p < db/schema.sql
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

默认端口 8090。此时只有 MySQL 和 Redis 参与，其他中间件全部关闭，应用照常启动。

### 3. 验证

```bash
curl http://127.0.0.1:8090/actuator/health
```

返回 `UP` 即成功。

### 4. 打开接口文档

Swagger UI 因 Spring Framework 6.2 的资源模式限制已关闭，OpenAPI 文档仍可访问：

```
http://127.0.0.1:8090/v3/api-docs
```

把该地址导入 Apifox、Postman 或任意 Swagger UI 即可查看全部接口。

---

## 四、场景一览

### 4.1 多级缓存（cache）

**问题**：缓存有三个经典失效模式，不加处理会让数据库在瞬间被打穿。

| 问题 | 现象 | 本项目的解法 |
|---|---|---|
| 穿透 | 查询不存在的 id，每次都打到数据库 | 缓存空值标记 + Redisson 布隆过滤器 |
| 击穿 | 热点 key 过期瞬间，全部请求涌向数据库 | 分布式锁重建，只有一个线程回源 |
| 雪崩 | 大量 key 同一时刻过期 | TTL 随机抖动，把过期时间打散 |
| 双写不一致 | 更新后读到旧值 | 先更新库再删缓存 + 延迟双删 |

**验证**：

```bash
# 空值标记方案
curl 'http://127.0.0.1:8090/api/cache/lab/penetration?count=2000&guarded=false'

# 布隆过滤器方案
curl 'http://127.0.0.1:8090/api/cache/lab/penetration?count=2000&guarded=true'
```

实测 2000 次不存在的 id 查询，空值标记约 440ms，布隆过滤器约 100ms，相差 4 倍以上。

**关键实现**：`framework/cache/MultiLevelCache.java`

读路径：L1 Caffeine → L2 Redis → 数据库，逐层回填。
写路径：更新数据库 → 删除缓存 → 延迟 500ms 再删一次。第二次删除是为了清掉「读请求在更新提交前加载到旧值、之后又写了回去」的残留。

L1 的 TTL 上限被限制在 60 秒，且所有失效都会通过 Redis 发布订阅广播给其他节点。这是刻意的：本地缓存无法跨节点失效，只能缩短它的生命周期来兜底。

### 4.2 Redis 经典场景（classic）

| 场景 | 数据结构 | 接口示例 |
|---|---|---|
| 排行榜 | ZSet | `POST /api/classic/rank/submit?board=game&member=alice&score=100` |
| UV 统计 | HyperLogLog | `POST /api/classic/uv/record?page=home&visitorId=u1` |
| 签到日历 | Bitmap | `POST /api/classic/sign?userId=u1` |
| 短链 | String + Snowflake | `POST /api/classic/short-link?url=https://example.com` |
| 附近的人 | GEO | `GET /api/classic/geo/nearby?longitude=116.40&latitude=39.90&radiusKm=5` |
| 延迟队列 | RDelayedQueue | `POST /api/classic/delay-queue/offer?payload=order-1&delaySeconds=5` |
| 发号器 | Snowflake / 号段 / INCR / UUID | `GET /api/classic/id?strategy=snowflake&count=1000` |
| 限流 | 四种算法 | `GET /api/classic/limiter/compare?limit=10&attempts=30` |
| 分布式锁 | Redisson RLock | `GET /api/classic/lock/with-lock?threads=8&loops=10` |

**限流算法对比**（同一 key、同一突发流量）：

```bash
curl 'http://127.0.0.1:8090/api/classic/limiter/compare?limit=10&windowSeconds=60&attempts=30&distributed=true'
```

四种算法的行为差异一目了然：固定窗口在边界处会放过两倍流量，滑动窗口精确但占内存，令牌桶允许突发，漏桶强制匀速。

### 4.3 秒杀（seckill）

**核心思路**：把库存决策从数据库搬到 Redis，用一条 Lua 脚本完成「查余额 + 扣减 + 记录用户」，全程无锁无事务。

```
请求 → 限流令牌桶 → 本地售罄标记 → Lua 原子扣库存 → 发消息 → 返回
                                                    ↓
                                              异步创建订单
```

**四道防线**：

1. `@RateLimited` 令牌桶，拒绝超出承载能力的流量
2. 本地售罄标记，Redis 都不用访问直接返回
3. Lua 脚本保证扣减与去重原子完成
4. 数据库 `(activity_id, user_id)` 唯一索引，兜底防重复购买

**验证零超卖**：

```bash
# 创建活动
curl -X POST http://127.0.0.1:8090/api/seckill/activities -H 'Content-Type: application/json' \
  -d '{"productId":1,"title":"秒杀测试","totalStock":10,"unitPrice":9.90,
       "startTime":"2026-08-01T00:00:00","endTime":"2026-12-31T00:00:00"}'

# 预热库存到 Redis
curl -X POST http://127.0.0.1:8090/api/seckill/activities/1/prepare

# 20 个用户抢 10 件
for u in $(seq 200 219); do
  curl -s -X POST "http://127.0.0.1:8090/api/seckill/activities/1/seckill?userId=$u&quantity=1" -o /dev/null
done

# 结果
curl http://127.0.0.1:8090/api/seckill/activities/1/stock   # 0
mysql -uroot -p -e "select count(*), sum(quantity) from dong_lab.seckill_order"
```

实测结果：库存 10 全部售出，订单 9 笔共 10 件，**零超卖、零丢失**。

### 4.4 抢红包（redpacket）

**算法**：二倍均值法。每次在 `[1, 2 × 均值 - 1]` 区间随机取值，保证每人期望相等且有惊喜，同时预留剩余人数的最低金额，避免最后一人拿到 0。

**架构**：发红包时就把金额算好推入 Redis List，抢的时候只是一次 `RPOP`。全程没有锁、没有事务、没有读改写，再多人同时点也不会竞争。

**验证金额守恒**：

```bash
PN=$(curl -s -X POST http://127.0.0.1:8090/api/red-packet/send \
  -H 'Content-Type: application/json' \
  -d '{"sponsorId":1,"totalAmount":10000,"totalCount":10,"packetType":2}' \
  | sed -E 's/.*"data":"?([^",]*)"?.*/\1/')

for u in $(seq 1 12); do
  curl -s -X POST "http://127.0.0.1:8090/api/red-packet/grab?packetNo=$PN&userId=$u" -o /dev/null
done

curl "http://127.0.0.1:8090/api/red-packet/remain?packetNo=$PN"
```

实测：10 人抢完，金额合计恰好 10000 分，分毫不差；第 11、12 人正确被拒。

### 4.5 微博模型（social）

**关注关系**用 Redis Set 存储，天然支持交集运算，共同关注就是一次 `SINTER`。

**Feed 流推拉两种模式都实现了**，可以对比：

| 模式 | 写操作 | 读操作 | 适用 |
|---|---|---|---|
| 推（写扩散） | 发一条，写给所有粉丝 | 直接读自己的时间线 | 粉丝少、读多 |
| 拉（读扩散） | 只写一份 | 拉取时聚合所有关注者 | 大 V、粉丝多 |

```bash
curl 'http://127.0.0.1:8090/api/social/timeline/push?userId=1&size=10'
curl 'http://127.0.0.1:8090/api/social/timeline/pull?userId=1&pageNum=1&pageSize=10'
```

微博的实际做法是推拉结合：大 V 走拉，普通用户走推。

### 4.6 Elasticsearch（search）

**IK 中文分词**，索引映射由 `SearchIndexInitializer` 在启动时显式创建（`category` 为 keyword 以支持聚合，`name`/`description` 用 `ik_max_word` 索引、`ik_smart` 查询）。

```bash
# 从 MySQL 全量同步
curl -X POST http://127.0.0.1:8090/api/search/sync

# 中文检索，带高亮和分面聚合
curl -G http://127.0.0.1:8090/api/search --data-urlencode 'keyword=云服务器'
```

返回包含高亮片段 `<em>云</em><em>服务器</em>测试商品` 和分类分面统计。

### 4.7 分布式事务 TCC（tcc）

**三阶段**：Try 冻结资源、Confirm 确认扣减、Cancel 释放冻结。

**三个必须处理的问题**：

| 问题 | 场景 | 处理 |
|---|---|---|
| 幂等 | 网络重试导致 Confirm 被调用两次 | 分支表 `(xid, branch_id)` 唯一索引 |
| 空回滚 | Try 未执行却收到 Cancel | 分支记录不存在时直接返回成功 |
| 悬挂 | Cancel 先到，Try 后到 | Try 前检查事务状态 |

**验证数据一致性**：

```bash
# 初始化库存 100、余额 100000
curl -X POST 'http://127.0.0.1:8090/api/tcc/seed?userId=1&productId=1&available=100&balance=100000'

# 成功提交
curl -X POST http://127.0.0.1:8090/api/tcc/order -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":1,"quantity":5}'
# → 库存 95、余额 95000、冻结归零

# 强制失败，验证回滚
curl -X POST http://127.0.0.1:8090/api/tcc/order -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":1,"quantity":5,"forceFailure":true}'
# → 数据完全不变，无残留冻结
```

`TccRecoveryTask` 每 30 秒扫描一次处于 CONFIRMING 状态的事务，决定推进还是回滚，保证宕机后也能自愈。

### 4.8 消息（mq）

**抽象层设计**：业务代码只依赖 `MessageProducer` 接口，`MqFacade` 根据 `lab.mq.active` 路由到具体实现。切换 Kafka 和 RocketMQ 是改配置，不是改代码。

| 传输 | 说明 | 状态 |
|---|---|---|
| `local` | JVM 内总线，无需任何中间件即可跑通全部流程 | 默认，已验证 |
| `rocketmq` | 延迟消息（18 个固定等级）、顺序消息、集群消费 | 已验证 |
| `kafka` | 顺序消息靠分区键，延迟消息靠「not before」头 + 消费端暂存 | 代码就绪，安装包下载中 |

**RocketMQ 已验证的能力**（`RocketMqListener` 复用统一的 `MessageHandler`，与本地总线同一套业务代码）：

```bash
# 普通消息
curl -X POST 'http://127.0.0.1:8090/api/mq/send?key=k1' --data-urlencode 'payload={"seq":1}'

# 顺序消息，同一 shardingKey 进入同一队列，按序消费
curl -X POST 'http://127.0.0.1:8090/api/mq/send-ordered?key=o1&shardingKey=shard-1'

# 延迟消息，5 秒后投递
curl -X POST 'http://127.0.0.1:8090/api/mq/send-delayed?key=d1&delaySeconds=5'
```

```bash
curl -X POST 'http://127.0.0.1:8090/api/mq/send?key=order-1' --data-urlencode 'payload={"amount":100}'
curl -X POST 'http://127.0.0.1:8090/api/mq/send-ordered?key=o1&shardingKey=order-1'
curl -X POST 'http://127.0.0.1:8090/api/mq/send-delayed?key=o2&delaySeconds=10'
curl http://127.0.0.1:8090/api/mq/stats
```

消费端幂等靠 `mq_message_log` 的唯一索引，重复投递只会入库一次，统计接口中的 `duplicated` 计数可以验证。

### 4.9 MongoDB（doc）

无 schema 日志存储。适合记录结构不固定、字段会随业务演进的数据。

```bash
curl -X POST http://127.0.0.1:8090/api/doc/operation-log -H 'Content-Type: application/json' \
  -d '{"bizType":"order","bizId":"1001","operator":"dong","action":"create","detail":{"amount":99}}'
```

### 4.10 MariaDB 第二数据源（replica）

独立的数据源、会话工厂和事务管理器，演示多数据源配置与跨库一致性观察。

```bash
curl -X POST 'http://127.0.0.1:8090/api/replica/accounts?userId=1&username=dong&balance=100000'
curl 'http://127.0.0.1:8090/api/replica/accounts/consistency?userId=1'
```

---

## 五、开关化设计

所有中间件默认关闭，应用只依赖 MySQL 和 Redis 即可启动。需要哪个就打开哪个。

```yaml
lab:
  mq:
    active: local          # local | rocketmq | kafka
  elasticsearch:
    enabled: false
  mongodb:
    enabled: false
  kafka:
    enabled: false
  rocketmq:
    enabled: false
  mariadb:
    enabled: false
  cache:
    l1-enabled: true       # Caffeine 本地缓存
    l2-enabled: true       # Redis 分布式缓存
```

关闭状态下访问相关接口，会返回明确的错误码 1004：

```json
{"code":1004,"message":"middleware is disabled, turn it on in application.yml first"}
```

而不是抛出一堆连接异常。

---

## 六、云服务器部署

服务器为 2G 内存，物理上无法同时运行全部中间件，因此采用**按需启停**的容器编排。

### 目录

```
/opt/dong-lab/
├── docker-compose.yml    编排文件，含内存限制与健康检查
├── initdb/               主库建表 SQL，首次启动自动执行
├── initdb-replica/       从库建表 SQL
└── lab.sh                启停脚本
```

### 使用

```bash
lab.sh core up      # MySQL + Redis，约 850M
lab.sh mq up        # Kafka + RocketMQ，约 1.1G
lab.sh search up    # Elasticsearch，约 900M
lab.sh doc up       # MongoDB，约 520M
lab.sh replica up   # MariaDB，约 390M

lab.sh core down    # 停止
lab.sh core ps      # 状态
lab.sh core logs    # 日志
```

### 已做的资源准备

- 停用旧项目的 6 个 Java 微服务（释放 1.28G 内存）
- 创建 4G swap 分区
- 停用宿主机原有的 Redis 与 MariaDB，避免端口冲突
- 安装 Docker 并配置国内镜像加速
- 编写 `deploy/elasticsearch/Dockerfile`，把 IK 分词器固化进镜像

### 连接远程环境

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=remote
```

已验证的全部链路：MySQL、Redis（Redisson）、MariaDB、MongoDB、Elasticsearch（含 IK 分词）。

### 关于 RocketMQ 与 Kafka

两个组件的代码层已完整（`RocketMqProducer`、`KafkaProducerAdapter`），切换只需改 `lab.mq.active`。部署层的情况：

- **RocketMQ**：已部署并验证通过。注意使用 4.9.7 版本（5.3.1 在此环境启动失败），且必须挂载 `broker.conf` 并设置 `brokerIP1` 为公网 IP，否则 namesrv 返回容器内网地址导致客户端连不上
- **Kafka**：Docker Hub 加速源无 `bitnami/kafka` 镜像，改为原生安装，安装包正在后台下载到 `/opt/kafka.tgz`

内存是硬约束。启动时请遵守按需原则：

```bash
lab.sh search down     # 先停 ES
lab.sh doc down        # 再停 MongoDB
lab.sh mq up           # 然后启动消息中间件
```

---

## 七、踩过的坑

这一节记录真实遇到的问题，比任何教程都值钱。

### 1. Redisson 脚本返回值的坑

最初用 `RedissonClient.getScript(StringCodec.INSTANCE).eval()` 执行 Lua 脚本，返回值被 `StringCodec` 错误处理，扣库存成功却返回 null，导致**库存已扣但系统认为失败**，8 件库存凭空消失。

改用 Spring Data Redis 的 `DefaultRedisScript<Long>` 后正常。教训：脚本执行路径要做 null 保护，宁可抛错也不要静默当成成功。

### 2. 枚举语义错误导致的状态误判

`DeductStatus.of(9)` 中 `9` 是「剩余库存」而非状态码，不匹配任何枚举值，默认返回 `NOT_PREPARED`。改成 `fromResult()`：负数映射错误状态，非负数一律为成功。

### 3. 虚拟线程与 Redisson 锁

JDK 21 虚拟线程的 `getId()` 不保证唯一，而 Redisson 可重入锁依赖线程 ID 标识持有者。改用平台线程池。

### 4. 锁超时被静默计入丢失

实验最初用 `waitTime=5s`，拿不到锁的线程抛异常后被吞掉，导致「加锁」场景也显示丢失更新。重构后区分 `lockAcquired` 和 `lockTimedOut`，语义才清晰。

### 5. ES 动态映射导致聚合失败

用原生 `ElasticsearchClient` 写入文档不会应用 `@Field` 注解，索引被动态创建成 text 类型，对 text 字段做 terms 聚合在 ES 中是非法操作，导致所有查询报错。改为启动时用原生客户端显式创建映射。

### 6. ES 日期格式不匹配

映射声明 `yyyy-MM-dd HH:mm:ss`，但 Jackson 输出 ISO-8601（`2026-08-29T13:29:35`），导致 bulk 写入全部失败且错误被忽略。改为 `strict_date_optional_time||epoch_millis`，并在 bulk 响应中检查 `errors()` 标志。

### 7. bulk 操作的静默失败

ES 的 bulk 接口即使单条失败，HTTP 也返回 200，必须检查响应体的 `errors()` 字段。加上检查后，日期格式问题立刻暴露。

### 8. Spring Data 反射在 JDK 21 下的限制

用 `indexOps.createMapping()` 生成映射时，Spring Data 反射访问 `BigDecimal` 内部字段，被 JDK 21 模块系统拒绝（`InaccessibleObjectException`）。除非加 `--add-opens` 参数，否则要用原生客户端显式定义映射。

### 9. 多数据源导致 Mapper 扫描失效

开启 MariaDB 后，其 `@MapperScan` 抑制了 MyBatis 的自动扫描，主库 Mapper 全部失效。需要显式声明主库的数据源、`sqlSessionFactory` 和事务管理器，并加 `@Primary`。

### 10. ES 配置位置写错

地址写在 `spring.data.elasticsearch.uris`，但代码和 Spring Boot 健康检查读的是 `spring.elasticsearch.uris`（默认 localhost:9200）。健康检查因此一直报连接拒绝。

### 11. springdoc 与 Spring Framework 6.2

springdoc 注册的资源模式 `/swagger-ui/**/*index.html` 被 Spring Framework 6.2 拒绝，导致启动失败。排除 `SwaggerConfig` 自动配置，OpenAPI 文档仍可正常生成。

### 12. 密码中的特殊字符

MongoDB 密码 `CHANGE_ME` 含 `@`，在 URI 中必须编码为 `%40`，否则解析为两个 `@` 导致认证失败。

### 13. IK 插件在容器重建后丢失

最初用 `docker exec` 在容器内手工安装 IK 插件，能正常工作。但 `docker compose down` 再 `up` 后容器被重建，插件消失，索引创建因找不到 `ik_max_word` 分析器而失败，集群状态变红。

正确做法是写进 `Dockerfile`，让插件成为镜像的一部分：

```dockerfile
FROM docker.elastic.co/elasticsearch/elasticsearch:8.15.3
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.15.3
```

教训：任何对运行中的容器做的手工修改都是一次性的，必须固化到镜像里。

### 14. bulk 写入的静默失败

ES 的 bulk 接口即使单条失败，HTTP 仍然返回 200。必须检查响应体的 `errors()` 字段并逐条读取 `item.error().reason()`。加上检查后，日期格式不匹配的问题立刻暴露出来——在此之前它只是让所有文档都写不进去，接口却返回成功。

### 15. RocketMQ 容器内网地址不可达

broker 启动后注册到 namesrv 的是容器内网 IP（`172.18.0.5`），客户端拿到这个地址去连，必然超时。必须在 `broker.conf` 里显式声明对外地址：

```properties
brokerIP1 = YOUR_PUBLIC_HOST
```

### 16. RocketMQ 版本与存储卷权限

5.3.1 版本在此环境启动即失败，换 4.9.7 正常。此外镜像以 `rocketmq` 用户运行，命名卷由 root 创建时无写权限，改用 bind mount 并授权后解决。

### 17. 顺序消息的 keys 丢失

`RocketMQTemplate.syncSendOrderly()` 会重建 Message 对象，导致设置的 `keys` 丢失，消费端取到 null，入库时触发非空约束。改用原生 `producer.send(message, selector, shardingKey)` 保留 keys，同时消费端做兜底：keys 为空时用 msgId。

---

## 八、编码规范（dong-standards）

本项目严格遵循 `dong-standards` 规范，要点如下：

| 规则 | 说明 |
|---|---|
| 禁止注释 | 包括 Javadoc、行内注释、SQL 注释。设计意图写在本文档中 |
| SQL 关键字小写 | `select`、`from`、`where`、`limit` 全部小写 |
| DDL 先行 | 先写建表语句，再写实体和 Mapper |
| 属性空行 | 实体每个属性之间空一行 |
| 方法空行 | 方法前后各空一行，方法体内不空行 |
| 文件末尾空行 | 每个文件以空行结尾 |
| 传统分层 | controller / service + impl / mapper / entity / dto / enums |
| 命名约定 | XxxController、XxxService + XxxServiceImpl、XxxMapper、XxxEntity、XxxRequest / XxxResponse |
| 统一响应 | `Result.success(data)` / `Result.fail(code, message)` |
| 状态用枚举 | 数据库存 int，Java 用枚举，配 TypeHandler 转换 |
| 时间类型 | `datetime` + `LocalDateTime`，格式 `yyyy-MM-dd HH:mm:ss` |
| 分页 | `PageRequest(pageNum, pageSize)` + `PageResult(total, list)`，Mapper 手写 `limit` |
| 事务 | `@Transactional(rollbackFor = Exception.class)` |
| 空集合 | 返回空集合而非 null |
| 常量 | 集中在 `Constants` 类 |

---

## 九、验证记录

以下结果均在 2 核 2G 云服务器上实测（Redis 跨网访问，单次往返约 5ms）。

| 场景 | 验证项 | 结果 |
|---|---|---|
| 秒杀 | 10 库存 / 20 人抢 | 售出 10 件，订单 9 笔共 10 件，零超卖零丢失 |
| 秒杀 | 3 库存 / 10 人抢 | 库存归零，无超卖 |
| 抢红包 | 10000 分 / 10 人抢 | 金额合计 10000 分，精确守恒 |
| 抢红包 | 3000 分 / 3 人抢 + 2 人超额 | 金额归零，超额者正确被拒 |
| 分布式锁 | 80 次并发自增 | 加锁零丢失；不加锁丢失 78（仅 2 次生效） |
| 缓存穿透 | 2000 次不存在 id | 空值标记 444ms；布隆过滤器 102ms |
| TCC | 成功 / 失败两分支 | 成功扣减正确；失败完全回滚，冻结残留 0 |
| TCC | 悬挂事务检查 | CONFIRMING 残留 0，TRIED 残留 0 |
| 消息幂等 | 重复投递 | 8 条消息 8 个唯一 id，重复被拦截 |
| ES 检索 | 中文分词 | 「云服务器」命中，高亮与分面正确 |
| ES 集群 | IK 映射 | category 为 keyword，name/description 用 IK |
| 推拉时间线 | 关注 2 人 + 动态 | 两种模式结果一致，未关注者不出现 |
| 多数据源 | MariaDB 独立事务 | 创建、转账、一致性检查全部正常 |
| 错误处理 | 重复创建账户 | 返回 1002 业务错误，不泄露数据库细节 |

共 25 项端到端接口验证，全部通过。
# dong
