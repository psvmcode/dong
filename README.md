# dong

中间件与分布式场景实验室。一个 Spring Boot 3 工程，把容易只停留在理论上的中间件用法，全部变成可以运行、可以对比、可以用数据验证的实验。

技术栈：Spring Boot 3.4.5 / JDK 21 / MySQL 8 / Redis 7 / Redisson 3.45 / MyBatis（原生 XML）/ RocketMQ 4.9.7 / Kafka 3.7 / Elasticsearch 8.15（IK 分词）/ MongoDB 7 / MariaDB 10.11

---

## 目录

| 章节 | 内容 |
|---|---|
| [一、项目定位](#一项目定位) | 这不是脚手架，是一组对照实验 |
| [二、能力速览](#二能力速览) | 十三个场景一张表看完 |
| [三、快速开始](#三快速开始) | 三步跑起来 |
| [四、目录结构与分层](#四目录结构与分层) | 限界上下文 + 传统分层 |
| [五、场景详解](#五场景详解) | 每个场景的问题、解法、验证命令 |
| [六、开关化设计](#六开关化设计) | 中间件按需开关 |
| [七、云服务器部署](#七云服务器部署) | 2 核 2G 下的按需启停 |
| [八、验证记录](#八验证记录) | 实测数据 |
| [九、踩过的坑](#九踩过的坑) | 真实问题与解法 |
| [十、编码规范](#十编码规范) | dong-standards |

深度内容不在本文，按方向进入：[场景文档](doc/scenario/README.md)（按业务场景）、[专题文档](doc/topic/README.md)（按主题纵向深挖）、[知识点学习](doc/learning/README.md)（脱离本项目的通用整理）。

---

## 一、项目定位

它不是脚手架，也不是业务系统，而是一组**可对照实验**。每个场景都回答三个问题：

1. **没有这个中间件时，问题长什么样**（错误现象 + 量化损失）
2. **用了之后，为什么能解决**（原理 + 关键代码）
3. **代价是什么**（性能、一致性、运维复杂度）

例如分布式锁，项目同时提供「不加锁」和「加 Redisson 锁」两个接口，8 线程 × 10 次并发自增实测：

| 模式 | 期望值 | 实际值 | 丢失更新 | 耗时 |
|---|---|---|---|---|
| 不加锁 | 80 | 2 | 78 | 280ms |
| Redisson 锁 | 80 | 80 | 0 | 18.4s |

数字本身就在说明问题：锁换来正确性，代价是 65 倍耗时。这类对照在全项目有三十余处。

---

## 二、能力速览

| 场景 | 接口前缀 | 核心中间件 | 关键能力 | 实测结论 |
|---|---|---|---|---|
| 多级缓存 | `/api/cache` | Caffeine + Redis | 穿透、击穿、雪崩、双写一致、熔断降级 | 穿透防护 444ms → 102ms |
| Redis 经典 | `/api/classic` | Redis + Redisson | 排行榜、UV、签到、短链、GEO、延迟队列、发号器、限流、分布式锁 | 加锁零丢失，代价 65 倍耗时 |
| 秒杀 | `/api/seckill` | Redis Lua + MQ | 预扣库存、异步下单、售罄短路 | 10 库存 20 人抢，零超卖 |
| 抢红包 | `/api/red-packet` | Redis List + MySQL 份额表 | 二倍均值预分配、限流、副本重建、数据库降级 | 金额精确守恒，Redis 全丢也能恢复 |
| 微博模型 | `/api/social` | Redis Set / ZSet | 关注关系、共同关注、推拉两种时间线 | 两种模式结果一致 |
| 搜索 | `/api/search` | Elasticsearch + IK | 中文分词、高亮、分面聚合 | 中文命中并高亮 |
| 分布式事务 | `/api/tcc` | MySQL | Try/Confirm/Cancel + 幂等、空回滚、悬挂 | 失败分支零残留 |
| 消息 | `/api/mq` | local / RocketMQ / Kafka | 顺序、延迟、批量、幂等 | 重复投递被拦截 |
| 文档 | `/api/doc` | MongoDB | 无 schema 日志 | 字段可随业务演进 |
| 多数据源 | `/api/replica` | MariaDB | 第二数据源、独立事务管理器 | 一致性检查通过 |
| 跨境支付 | `/api/crossborder` | MySQL + MQ + Redis | 幂等、锁汇、合规筛查、人工审核、冻结、异步清算、对账 | 金额精确到分，余额流水一致 |
| 订单状态机 | `/api/order` | MySQL + COLA StateMachine | 守卫、内部迁移、条件分支回退、乐观锁并发 | 16 线程抢推，仅 1 次成功 |
| Agent 工程实验室 | `/api/agent` | MySQL + LLM（OpenAI 兼容） | 工具调用循环、五道停止闸门、轨迹落库、SSE 流式、幂等 | mock 跑通一轮：2 步、2 次并行工具调用按序回填、`finish_reason=STOP` |

> 全站接口统一为 POST：写操作仍可用 query 参数，查询类参数一律走 JSON body，分页参数见 `PageQuery`。

---

## 三、快速开始

**不在本地运行任何中间件**，全部连云服务器，开发机只跑应用进程本身。

### 凭据：deploy/.env

账号密码集中在一个文件，已被 git 忽略（模板见 `deploy/.env.example`）：

```bash
cat deploy/.env                  # 查看，访达按 Cmd+Shift+. 显示隐藏文件
git check-ignore -v deploy/.env  # 有输出即已被忽略
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env   # 首次创建
```

`application.yml` 通过 `spring.config.import` 自动读它，**无需手动 source**。变量缺失时应用直接启动失败并提示 `Could not resolve placeholder 'LAB_XXX'`，不会静默回落到本地——避免「以为连的是线上、实际连到本地脏数据」。

MongoDB 密码含 `@` 要填 URL 编码后的值（`P@ssw0rd` → `P%40ssw0rd`）。

### 启动

```bash
mvn spring-boot:run                                  # 端口 8090
curl http://127.0.0.1:8090/actuator/health           # 返回 UP 即成功
```

接口文档 `http://127.0.0.1:8090/doc.html`（Knife4j），141 个接口，支持中文、搜索、在线调试；原始数据 `/v3/api-docs` 可导入 Apifox / Postman。

### 建表

云服务器的 MySQL / MariaDB 由 `deploy/initdb`、`deploy/initdb-replica` 在首次启动时自动建表。

**这两个目录是 `db/schema.sql` 的生成物，禁止手工编辑**：加表只改 `db/schema.sql`，再执行 `./deploy/gen-initdb.sh`。容器侧的 `/docker-entrypoint-initdb.d` 只在数据目录为空时执行一次，所以生成物必须剥掉 `create database` 与 `use`。

这个缺口平时测不出来——现有容器早已初始化过，脚本不会再跑，只有删掉 volume 全新部署时才暴露为"表不存在"。

---

## 四、目录结构与分层

顶层按**限界上下文**划分，每个上下文内部按**传统分层**组织。既保住场景之间的边界，又符合后端团队的阅读习惯。

```
src/main/java/com/dong/
├── common/          共享内核：Constants、Result、异常、工具类
├── config/          全局装配：数据源、Redisson、线程池、OpenAPI、WebMvc
├── framework/       技术能力层，与业务无关，可被任意上下文复用
│   ├── redis/       RedisService 门面，脚本参数统一转字符串
│   ├── lock/        分布式锁接口 + LockHandle
│   ├── cache/       多级缓存、失效总线、统计、熔断
│   ├── limiter/     RateLimiter 接口与 @RateLimited 切面
│   ├── bloom/       Redisson 布隆过滤器
│   └── mq/          MessageProducer 接口与 MqFacade 路由
├── cache/ classic/ seckill/ redpacket/ social/
├── search/ tcc/ mq/ doc/ replica/ crossborder/ order/ agent/
└── 上下文内部：controller / service + impl / mapper / entity / dto / enums / support / task / handler

db/schema.sql        建表语句，唯一权威源
deploy/              Docker Compose 编排与启停脚本
```

**配置只有一份** `application.yml`，没有按环境拆分的 profile：与部署位置无关的策略写在文件里，连接串与账号一律走 `${LAB_*}` 环境变量，文件内不含任何明文凭据。

```yaml
spring:
  config:
    import: optional:file:./deploy/.env[.properties]
```

`[.properties]` 后缀必需：Spring Boot 3.4.5 没有内置 dotenv 加载器，无法凭 `.env` 这个隐藏文件名判断格式。

Elasticsearch 与 MongoDB 的健康检查指示器是关闭的——它们是可选组件，没启动时不该把整个应用标成 DOWN，只有 MySQL 与 Redis 决定 `/actuator/health` 的状态。

### 分层约定

| 目录 | 职责 | 禁止 |
|---|---|---|
| `controller` | 参数校验、调 service、封装 Result | 写业务逻辑 |
| `service` / `impl` | 接口定义 / 业务逻辑与事务边界 | 直接依赖具体中间件实现 |
| `task` | 定时任务，只做调度与编排 | 写业务逻辑 |
| `handler` | 消息处理器，消费后转调 service | 直接改状态或直接写库 |
| `support` | 无状态工具与进程内组件 | 持有业务状态、依赖 mapper |
| `mapper` / `entity` | 数据访问 / 与表一一对应 | 写业务逻辑 |
| `dto` / `enums` | 请求响应对象 / 状态码（落库 int） | 与 entity 混用、魔法数字 |

接口与实现一律分包，`framework` 技术层同理：接口留本包，实现进各自的 `impl`。定时任务、消息处理器、无状态工具不进 `service`——否则 `service` 包里会混进一批没有接口的类，接口与实现的对应关系就不再可信。

---

## 五、场景详解

### 5.1 多级缓存（cache）

**三个经典失效模式**，不处理会让数据库在瞬间被打穿：

| 问题 | 现象 | 解法 |
|---|---|---|
| 穿透 | 查不存在的 id，每次都打到库 | 空值标记 + 布隆过滤器 |
| 击穿 | 热点 key 过期瞬间全部回源 | 分布式锁重建，只有一个线程回源 |
| 雪崩 | 大量 key 同时过期 | TTL 随机抖动，把过期时间打散 |
| 双写不一致 | 更新后读到旧值 | 先更新库再删缓存 + 延迟双删 |

实测 2000 次不存在的 id 查询：空值标记 444ms，布隆过滤器 102ms。

**缓存不只做加速，故障时必须能降级**。读路径返回四态，调用方能区分「没有数据」与「拿不到数据」：

| 情况 | 行为 |
|---|---|
| 命中 | 正常返回 |
| 空值标记 / 布隆判定不存在 | 1001，不回源 |
| 回源失败但缓存里有逻辑过期的旧值 | 返回旧值 + `stale=true`，响应消息提示数据可能不是最新 |
| 回源失败且无旧值 | 1005 依赖不可用，明确失败 |

两条硬规则：**回源失败绝不写空值标记**（那等于把一次故障固化成"数据不存在"）；**熔断必须快**（不熔断时每个请求都要陪下游等满超时，"慢"会放大成"不可用"）。

防护分层，任一层失效都不会让数据库直接暴露：

```
IP 限流 → 扫描封禁 → L1 → L2（带熔断）→ 回源熔断 → 回源频控 → 重建锁 → 数据库
```

回源频控与扫描检测刻意用**本地**实现：它们是保护数据库的闸门，自己不能再依赖 Redis，否则 Redis 一出故障限流先失效，压力反而完整灌进来。

```bash
curl -X POST http://127.0.0.1:8090/api/cache/lab/penetration -H 'Content-Type: application/json' \
  -d '{"count":2000,"guarded":true}'
curl -X POST http://127.0.0.1:8090/api/cache/lab/stats   # 命中率、staleServed、degraded、circuitBlocked
```

关键实现 `framework/cache/MultiLevelCache.java`。L1 的 TTL 上限压到 60 秒，所有失效通过 Redis 发布订阅广播给其他节点——本地缓存无法跨节点失效，只能缩短生命周期兜底。

### 5.2 Redis 经典场景（classic）

| 场景 | 数据结构 | 接口 |
|---|---|---|
| 排行榜 | ZSet | `POST /rank/submit`、`/rank/top`、`/rank/settle-weekly` |
| UV 统计 | HyperLogLog | `POST /uv/record`、`/uv/range` |
| 签到 | Bitmap | `POST /sign`、`/sign/streak`、`/sign/calendar` |
| 短链 | String + Snowflake | `POST /short-link`、`/short-link/resolve`、`/short-link/hits` |
| 附近的人 | GEO | `POST /geo/nearby`、`/geo/distance` |
| 延迟队列 | RDelayedQueue | `POST /delay-queue/offer`、`/delay-queue/take` |
| 发号器 | Snowflake / 号段 / INCR / UUID | `POST /id` |
| 限流 | 四种算法 | `POST /limiter/try`、`/limiter/compare` |
| 分布式锁 | Redisson RLock | `POST /lock/with-lock`、`/lock/without-lock` |

前缀均为 `/api/classic`。完整参数见 [经典场景说明](doc/scenario/classic.md)。

**限流四算法对比**：只打一轮突发区分不出算法（窗口内都最多放行 `limit` 个），差异在配额如何恢复，所以要看第二轮。

| 参数 | fixed_window | sliding_window | token_bucket | leaky_bucket |
|---|---|---|---|---|
| 第一轮放行 | 10 | 10 | 11~12 | 10~12 |
| 间隔 3 秒后 | 0 | 0 | 6 | 6~7 |
| 间隔 3.5 秒后 | 10 | 0 | 7 | 7 |
| 间隔 5 秒后 | 10 | 10 | 10 | 10 |

固定窗口跨过边界就一次性归零；滑动窗口最严格；令牌桶与漏桶第一轮略超 `limit` 不是超额——它们限制的是平均速率而非窗口内瞬时总量。

```bash
curl -X POST http://127.0.0.1:8090/api/classic/limiter/compare -H 'Content-Type: application/json' \
  -d '{"bizKey":"demo","limit":10,"windowSeconds":6,"attempts":30,"distributed":true,"gapMillis":3500}'
```

四种算法用 Lua 自行实现，因为 Redisson 的 `RRateLimiter` 底层只有令牌桶一种，四种会退化成同一种行为（见第九节）。

**锁的对照实验** `POST /lock/without-lock` 与 `/lock/with-lock`，返回结果区分 `lockAcquired` 与 `lockTimedOut`，拿不到锁的线程不会被静默计入丢失。

### 5.3 秒杀（seckill）

**核心思路**：把库存决策从数据库搬到 Redis，一条 Lua 脚本完成「查余额 + 扣减 + 记录用户」，全程无锁无事务。

```
请求 → 限流令牌桶 → 本地售罄标记 → Lua 原子扣库存 → 发消息 → 返回
                                                  ↓
                                            异步创建订单
```

四道防线：`@RateLimited` 令牌桶 → 本地售罄标记（连 Redis 都不访问）→ Lua 保证扣减与去重原子完成 → 数据库 `(activity_id, user_id)` 唯一索引兜底防重复购买。

```bash
# 10 库存，20 个用户抢
curl -X POST http://127.0.0.1:8090/api/seckill/activities -H 'Content-Type: application/json' \
  -d '{"productId":1,"title":"秒杀测试","totalStock":10,"unitPrice":9.90}'
curl -X POST http://127.0.0.1:8090/api/seckill/activities/1/prepare
for u in $(seq 200 219); do
  curl -s -X POST "http://127.0.0.1:8090/api/seckill/activities/1/seckill?userId=$u&quantity=1" -o /dev/null
done
curl -X POST http://127.0.0.1:8090/api/seckill/activities/1/stock   # 0
```

实测：库存 10 全部售出，订单 9 笔共 10 件，**零超卖、零丢失**。超时未支付由定时任务取消并回滚库存（`dong.seckill.payment-timeout-minutes`，默认 15 分钟）。

### 5.4 抢红包（redpacket）

**算法**二倍均值法：每次在 `[1, 2 × 均值 - 1]` 区间取值，期望相等又有惊喜，同时为剩余人数预留最低金额。

**架构**：发红包时金额按份算好，既落库成 `red_packet_item` 份额表，也推入 Redis List；抢的时候只是一次 `RPOP` 弹出「序号:金额」，库里再用 `status = 0` 条件占位。全程无锁、无读改写。

| 防线 | 挡的是什么 |
|---|---|
| 单用户限流（滑动窗口 60/分） | 脚本刷接口；Redis 故障时放行，不把中间件故障放大成业务不可用 |
| 本地抢完标记（10 秒短路） | 抢完之后仍然打 Redis 的无效流量 |
| Lua 原子弹出 | 同一份被两人拿到、重复抢 |
| 份额表 `status = 0` 占位 | 副本与库存不一致时的超发 |
| 扣减带充足条件 | 剩余金额被扣成负数 |

**Redis 只是副本**：队列丢失时先从份额表重建再抢；Redis 整体不可用时降级到数据库（随机起点 + `status = 0` 占位），慢一点但一样不超发。落库失败会把预扣份额原样归还。

```bash
PN=$(curl -s -X POST http://127.0.0.1:8090/api/red-packet/send -H 'Content-Type: application/json' \
  -d '{"sponsorId":1,"totalAmount":10000,"totalCount":10,"packetType":2}' \
  | sed -E 's/.*"data":"?([^",]*)"?.*/\1/')
for u in $(seq 1 12); do
  curl -s -X POST "http://127.0.0.1:8090/api/red-packet/grab?packetNo=$PN&userId=$u" -o /dev/null
done
curl -X POST http://127.0.0.1:8090/api/red-packet/remain -H 'Content-Type: application/json' -d "{\"packetNo\":\"$PN\"}"
```

实测：10 人抢完金额合计恰好 10000 分，第 11、12 人正确被拒；手动清掉 Redis 库存键再抢，服务自动从份额表重建，金额仍精确守恒。

### 5.5 微博模型（social）

关注关系用 Redis Set，共同关注就是一次 `SINTER`。Feed 流推拉两种模式都实现了：

| 模式 | 写操作 | 读操作 | 适用 |
|---|---|---|---|
| 推（写扩散） | 发一条，写给所有粉丝 | 直接读自己的时间线 | 粉丝少、读多 |
| 拉（读扩散） | 只写一份 | 拉取时聚合所有关注者 | 大 V、粉丝多 |

实测两种模式结果一致。真实系统是推拉结合：大 V 走拉，普通用户走推。

### 5.6 Elasticsearch（search）

IK 中文分词，映射由 `SearchIndexInitializer` 启动时显式创建（`category` 为 keyword 以支持聚合，`name`/`description` 用 `ik_max_word` 索引、`ik_smart` 查询）。

```bash
curl -X POST http://127.0.0.1:8090/api/search/sync    # 从 MySQL 全量同步
curl -X POST http://127.0.0.1:8090/api/search -H 'Content-Type: application/json' -d '{"keyword":"云服务器"}'
# → 高亮片段 <em>云</em><em>服务器</em>测试商品 + 分类分面统计
```

### 5.7 分布式事务 TCC（tcc）

三阶段 Try 冻结资源、Confirm 确认扣减、Cancel 释放冻结。三个必须处理的问题：

| 问题 | 场景 | 处理 |
|---|---|---|
| 幂等 | 网络重试导致 Confirm 被调用两次 | 分支表 `(xid, branch_id)` 唯一索引 |
| 空回滚 | Try 未执行却收到 Cancel | 分支记录不存在时直接返回成功 |
| 悬挂 | Cancel 先到、Try 后到 | Try 前检查事务状态 |

```bash
curl -X POST 'http://127.0.0.1:8090/api/tcc/seed?userId=1&productId=1&available=100&balance=100000'
curl -X POST http://127.0.0.1:8090/api/tcc/order -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":1,"quantity":5}'                                  # → 库存 95、余额 95000、冻结归零
curl -X POST http://127.0.0.1:8090/api/tcc/order -H 'Content-Type: application/json' \
  -d '{"userId":1,"productId":1,"quantity":5,"forceFailure":true}'              # → 数据完全不变，无残留冻结
```

`TccRecoveryTask` 每 30 秒扫描 CONFIRMING 状态的事务，决定推进还是回滚，宕机后也能自愈。

### 5.8 消息（mq）

业务代码只依赖 `MessageProducer` 接口，`MqFacade` 按 `dong.mq.active` 路由。切换传输是改配置，不是改代码。

| 传输 | 说明 | 状态 |
|---|---|---|
| `local` | JVM 内总线，无需任何中间件即可跑通全部流程 | 默认，已验证 |
| `rocketmq` | 延迟消息（18 个固定等级）、顺序消息、集群消费 | 已部署并验证 |
| `kafka` | 顺序靠分区键，延迟靠「not before」头 + 消费端暂存 | 编排就绪，按需启动 |

消费端幂等靠 `mq_message_log` 唯一索引，重复投递只入库一次，`POST /api/mq/stats` 的 `duplicated` 计数可验证。

### 5.9 MongoDB（doc）

无 schema 日志存储，适合结构不固定、字段随业务演进的数据。`POST /api/doc/operation-log/save` 写入，`/page` 分页查，`/count` 计数。

### 5.10 MariaDB 第二数据源（replica）

独立的数据源、会话工厂和事务管理器。`POST /api/replica/accounts/consistency` 连读两次，可暴露主从延迟问题。

### 5.11 跨境支付（crossborder）

> 完整业务场景、技术场景与接口清单见 [`doc/scenario/crossborder.md`](doc/scenario/crossborder.md)，原理与风控判定见 [`doc/topic/crossborder-payment.md`](doc/topic/crossborder-payment.md)。

这是最贴近真实业务的一个场景，一笔汇款要过九个环节，每个环节都有对应的工程问题：

```
汇款申请 → 幂等校验 → 合规筛查 →（大额挂起 → 人工审核）→ 锁定汇率 → 扣款记账 → 异步清算 → 收款入账 → 对账核销
```

| 环节 | 真实问题 | 本项目的做法 |
|---|---|---|
| 汇款申请 | 超时重试导致重复汇款 | `idempotent_key` 唯一索引 + 分布式锁，重放返回原单 |
| 合规筛查 | 制裁名单命中必须拒绝 | 名单放 Redis Set（O(1) 匹配），四道检查逐条留痕 |
| 人工审核 | 大额交易合法但可疑 | 超 5 万挂起 PENDING_REVIEW，放行或驳回，决策落库 |
| 账户冻结 | 反洗钱调查期间不能交易 | 冻结/解冻，原因与操作人落事件表，余额流水完整保留 |
| 锁定汇率 | 汇率波动，不锁汇银行担敞口 | 报价带有效期，乐观锁锁定，过期作废 |
| 风控限额 | 日累计限额并发下被突破 | Lua 原子完成累加与判断，失败与驳回释放占用 |
| 扣款记账 | 扣款与记账必须原子 | 同一本地事务，余额扣减带充足条件防负数 |
| 异步清算 | 渠道只有批量清算窗口 | RocketMQ 顺序消息推进，补偿任务兜底 |
| 收款入账 | 重复消息导致重复入账 | 消费幂等 + 流水唯一索引兜底 |
| 对账核销 | 渠道回单与本地流水不一致 | 差异表记录长款短款，运营按类型处理 |

| 技术问题 | 解法 |
|---|---|
| 接口幂等 | 先查幂等键 → 分布式锁内双查 → 唯一索引兜底，重放一律返回原单 |
| 状态机并发 | `update ... where status=期望 and version=当前` 乐观锁，抢占失败方报冲突或幂等返回 |
| 限额原子性 | Lua 内完成「累加 + 判断」，失败与驳回路径释放占用，杜绝额度泄漏 |
| 事务边界 | `TransactionSynchronization.afterCommit` 注册回调，提交成功才发消息 |
| 事务代理失效 | 账务操作独立成 `CrossBorderLedgerService` bean，事务真正生效 |
| 失败补偿 | 留下 FUNDS_DEBITED 单子，定时任务扫描补偿推进，资金在本地不回滚 |
| 留痕不可篡改 | 合规记录与账户事件表只增不改，无更新接口 |
| 资金自检 | 贷方减借方反推余额与实际比对，diff 接口随时可查 |

**为什么选本地事务加消息而不是 TCC**：清算要经过外部渠道，渠道本身只支持异步批量，把它拉进强一致事务既做不到也没必要，最终一致加对账兜底才是支付系统的实际做法。

**实测**：1000 CNY 汇往 USD，CIPS 渠道（固定费 10 加万分之五），按锁定汇率 0.14006993 成交，收款方精确收到 140.07 USD，付款方扣款 1010.50 CNY，余额与流水差额为零。

**一个真实事故级别的坑**：入账逻辑最初写在消费者内部方法上，`this` 调用绕过 Spring 事务代理，事务静默失效。并发到达的重复消息各自提交了加钱，流水唯一索引冲突却回滚不了已提交的余额变更，收款方余额被重复累加。修复方式是把账务操作拆到独立的 bean，让事务真正经过代理生效。

### 5.12 订单履约状态机（order）

全项目唯一引入第三方状态机组件的场景，用 COLA StateMachine 把履约规则显式化。状态只能由事件推进，接口层不提供任何直接改状态的入口——绕过状态机的口子一旦开了一个，规则迟早会被绕过。

**七种状态**：待支付 → 待发货 → 待收货 → 已完成，外加已取消、退款中、已退款。**九种事件**：支付、取消、超时、发货、确认收货、催单、申请退款、退款成功、退款失败。

```bash
curl -X POST http://127.0.0.1:8090/api/order -H 'Content-Type: application/json' \
  -d '{"userId":1001,"productName":"机械键盘","quantity":1,"payAmount":399.00}'
curl -X POST http://127.0.0.1:8090/api/order/TO.../available-events    # → ["PAY","CANCEL","TIMEOUT"]
# 待支付直接发货 → order TO... cannot handle SHIP
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"SHIP","trackingNo":"SF001"}'
# 支付不传流水号 → guard rejected event PAY from WAIT_PAY
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' -d '{"event":"PAY"}'
```

**并发实验**：16 线程同时对同一张订单发货，各跑 5 轮。带乐观锁时 `successCount` 恒为 1、`finalVersion` 恒为 2；不带时 `successCount` 在 13~16 间浮动，而 `finalVersion` 始终是 1——这些「成功」只是互相覆盖，连谁先谁后都无从追溯。

两个认知要点：

- **状态机管不了并发**。COLA 状态机是无状态的，只回答「从某状态收到某事件该去哪」，真正的并发安全靠落库时的 `where status=期望 and version=当前`。少了状态机非法跃迁能绕过，少了乐观锁两个线程能同时推进同一个订单。
- **被拒绝不等于没发生**。`fireEvent` 在迁移被拒时返回原状态，与内部迁移的返回值完全一样。因此每个 action 都往上下文写 `accepted` 标记，只有 action 真被执行过才算通过。

---

## 六、开关化设计

中间件开关集中在唯一的 `application.yml`，默认全部启用。本地只验证某项功能时，用命令行参数临时关掉不需要的组件，不改配置文件：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--dong.mariadb.enabled=false --dong.rocketmq.enabled=false"
```

```yaml
dong:
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
    breaker-enabled: true  # 回源熔断
    guard-enabled: true    # 读防护：限流与扫描封禁
```

关闭状态下访问相关接口返回明确的错误码 1004，而不是抛一堆连接异常：

```json
{"code":1004,"message":"middleware is disabled, turn it on in application.yml first"}
```

---

## 七、云服务器部署

服务器为 2 核 2G，物理上无法同时运行全部中间件，因此采用**按需启停**的容器编排。

### 目录与凭据

```
/opt/dong-lab/
├── docker-compose.yml    编排文件，含内存限制与健康检查
├── .env                  密码与公网地址（不进版本库）
├── setup-env.sh          服务器上交互式生成 .env
├── print-env.sh          本地执行，打印可粘贴到服务器的命令
├── gen-initdb.sh         从 db/schema.sql 生成下面两个目录，加表后必须跑
├── initdb/               主库建表 SQL（生成物，勿手改）
├── initdb-replica/       从库建表 SQL（生成物，勿手改）
├── elasticsearch/        含 IK 插件的 Dockerfile
├── rocketmq/             broker.conf 模板，含 ${LAB_PUBLIC_HOST} 占位符
└── lab.sh                启停脚本
```

仓库不保存密码，`cd /opt/dong-lab && ./setup-env.sh` 交互式生成（密码不回显，文件权限 600）。若服务器只能粘贴命令不能传文件，可在本地跑 `deploy/print-env.sh`，它会打印一段可直接执行的命令。

| 变量 | 说明 |
|---|---|
| `LAB_PUBLIC_HOST` | 服务器公网 IP 或域名，RocketMQ 与 Kafka 对外声明的地址 |
| `LAB_MYSQL_USERNAME` / `LAB_MYSQL_PASSWORD` | MySQL 账号密码 |
| `LAB_REDIS_PASSWORD` | Redis 密码 |
| `LAB_MARIADB_USERNAME` / `LAB_MARIADB_PASSWORD` | MariaDB 账号密码 |
| `LAB_ES_PASSWORD` | Elasticsearch 的 `elastic` 用户密码 |
| `LAB_MONGO_USERNAME` / `LAB_MONGO_PASSWORD` | MongoDB 账号密码（含 `@` 要填 `%40`） |

变量缺失时 compose 直接拒绝启动并提示缺哪个，不会带着空密码跑起来。

### 服务清单与启停

| 服务 | 端口 | profile |
|---|---|---|
| MySQL | 3306 | core |
| Redis | 6379 | core |
| RocketMQ | 9876 / 10911 | mq |
| Kafka | 9092 | kafka |
| Elasticsearch | 9200 | search |
| MongoDB | 27017 | doc |
| MariaDB | 3307 | replica |

```bash
lab.sh core up      # MySQL + Redis
lab.sh mq up        # RocketMQ（namesrv + broker）
lab.sh search up    # Elasticsearch（含 IK）
lab.sh doc up       # MongoDB
lab.sh replica up   # MariaDB
lab.sh full up      # 全部，2G 内存下不建议
lab.sh core down | ps | logs
```

若云端只起了一部分，把未启动的组件临时关掉，否则应用会卡在连接失败上：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--dong.mongodb.enabled=false --dong.elasticsearch.enabled=false"
```

### 内存是硬约束

| 组件 | 上限 | 实测 | 关键参数 |
|---|---|---|---|
| Elasticsearch | 800m | 616MB | `-Xmx512m -XX:MaxDirectMemorySize=192m`，堆外不设限会与堆等大 |
| RocketMQ broker | 420m | 266MB | `-Xmx256m -Xmn128m`；与 namesrv 是两个独立 JVM |
| RocketMQ namesrv | 220m | 39MB | `-Xmx128m -Xmn64m` |
| Kafka | 512m | — | `-Xmx384M -Xms192M` |
| MongoDB | 400m | 98MB | `--wiredTigerCacheSizeGB 0.25`，按宿主内存算而非容器上限 |
| MySQL | 320m | 114MB | `innodb_buffer_pool_size=64M`、`performance_schema=OFF` |
| MariaDB | 256m | 44MB | `innodb_buffer_pool_size=48M`、`performance_schema=OFF` |
| Redis | 128m | 7MB | `maxmemory 64mb` |

| 组合 | 实测占用 | 可行性 |
|---|---|---|
| core（MySQL + Redis） | 121MB | 建议常驻，几乎所有模块都依赖 |
| core + replica / doc | 165 / 219MB | 无压力 |
| core + mq / search | 426 / 737MB | 宽松 |
| 全部（不含 Kafka） | 1184MB | 可行 |

结论：清理重复实例后全部中间件可同时长期运行，唯一需要斟酌的是 ES——它占 616MB 且只有 search 模块用得到，内存紧张时优先关它。`mem_limit` 只是天花板，真实占用看 `docker stats`。

改完配置必须 `docker compose --profile core up -d --force-recreate` 才生效。

---

## 八、验证记录

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
| 消息幂等 | 重复投递 | 唯一 id 数等于消息数，重复被拦截 |
| ES 检索 | 中文分词 | 「云服务器」命中，高亮与分面正确 |
| 推拉时间线 | 关注 2 人 + 动态 | 两种模式结果一致，未关注者不出现 |
| 多数据源 | MariaDB 独立事务 | 创建、转账、一致性检查全部正常 |
| 错误处理 | 重复创建账户 | 返回 1002 业务错误，不泄露数据库细节 |

共 25 项端到端接口验证，全部通过。

---

## 九、踩过的坑

这一节记录真实遇到的问题，比任何教程都值钱。

### 1. Redisson 脚本返回值

最初用 `RedissonClient.getScript(StringCodec.INSTANCE).eval()` 执行 Lua，返回值被 `StringCodec` 错误处理，扣库存成功却返回 null，导致**库存已扣但系统认为失败**，8 件库存凭空消失。改用 Spring Data Redis 的 `DefaultRedisScript<Long>` 后正常。教训：脚本执行路径要做 null 保护，宁可抛错也不要静默当成成功。

### 2. 枚举语义错误导致状态误判

`DeductStatus.of(9)` 中 `9` 是「剩余库存」而非状态码，不匹配任何枚举值，默认返回 `NOT_PREPARED`。改成 `fromResult()`：负数映射错误状态，非负数一律为成功。

### 3. 虚拟线程与 Redisson 锁

JDK 21 虚拟线程的 `getId()` 不保证唯一，而 Redisson 可重入锁依赖线程 ID 标识持有者。改用平台线程池。

### 4. 锁超时被静默计入丢失

实验最初用 `waitTime=5s`，拿不到锁的线程抛异常后被吞掉，导致「加锁」场景也显示丢失更新。重构后区分 `lockAcquired` 和 `lockTimedOut`，语义才清晰。

### 5. Elasticsearch 的四连坑

- **动态映射导致聚合失败**：原生客户端写入不应用 `@Field` 注解，索引被动态创建成 text 类型，对 text 做 terms 聚合在 ES 中是非法操作。改为启动时用原生客户端显式创建映射。
- **日期格式不匹配**：映射声明 `yyyy-MM-dd HH:mm:ss`，Jackson 输出 ISO-8601，bulk 写入全部失败且错误被忽略。改为 `strict_date_optional_time||epoch_millis`。
- **bulk 静默失败**：bulk 接口即使单条失败 HTTP 也返回 200，必须检查响应体的 `errors()` 并逐条读 `item.error().reason()`。加上检查后日期问题立刻暴露。
- **Spring Data 反射在 JDK 21 被拒**：`indexOps.createMapping()` 反射访问 `BigDecimal` 内部字段，触发 `InaccessibleObjectException`。除非加 `--add-opens`，否则只能用原生客户端定义映射。
- **配置位置写错**：地址写在 `spring.data.elasticsearch.uris`，而代码与健康检查读的是 `spring.elasticsearch.uris`（默认 localhost:9200），健康检查因此一直报连接拒绝。

### 6. 多数据源导致 Mapper 扫描失效

开启 MariaDB 后，其 `@MapperScan` 抑制了 MyBatis 自动扫描，主库 Mapper 全部失效。需要显式声明主库的数据源、`sqlSessionFactory` 和事务管理器并加 `@Primary`；此后每加一个走主库的上下文，都要在 `config/PrimaryMybatisConfig` 的 `basePackages` 里补一行——只在接口上加 `@Mapper` 不够，自动扫描会把它注册到另一个 SessionFactory。

### 7. 接口文档的三连坑

- **springdoc 与 Spring Framework 6.2**：springdoc 的 UI 配置会注册 `/swagger-ui/**/*index.html` 这类资源模式，Spring 6.2 的路径解析器拒绝它，**直接导致启动失败**。注册这段逻辑的是 `SwaggerConfig` 里的一个 bean，直接排除整个 `SwaggerConfig` 又会丢掉 Knife4j 依赖的 `/v3/api-docs/swagger-config`，所以只移除那一个 bean（用 `BeanDefinitionRegistryPostProcessor`，因为 `removeBeanDefinition` 属于 `BeanDefinitionRegistry`）。
- **Knife4j starter 与新版 springdoc 无法共存**：引入 starter 后，其自带的 springdoc 2.3.0 报 `NoSuchMethodError: ControllerAdviceBean.<init>(Object)`（该构造函数在 Spring 6.2 已移除）；强制覆盖为 springdoc 2.9.0 又报 `getGroupConfigs()`（Knife4j 按 `List` 编译，2.4.0 起 springdoc 改成了 `Set`）。2.4.0 之后都是 `Set`，只有 2.3.0 是 `List`，不存在两者都满足的版本。解法是只引入 `knife4j-openapi3-ui` 这个**纯静态资源包**（不含任何 class），页面由它提供，数据仍由 springdoc 2.9.0 生成。代价是失去后端增强，核心的展示与调试不受影响。
- **自建 Swagger 页面的版本号耦合**：`static/swagger-ui/index.html` 写死了 webjar 版本，升级 springdoc 时传递依赖版本变化会导致 404。`SwaggerUiConfig` 启动时会校验并告警。

### 8. 密码中的特殊字符

MongoDB 密码含 `@`（如 `P@ssw0rd`）在连接 URI 中必须编码为 `%40`，否则 URI 被解析成两个 `@`，主机与认证信息错位，连接直接失败。

### 9. IK 插件在容器重建后丢失

用 `docker exec` 手工安装的插件，`docker compose down` 再 `up` 后就没了，索引创建因找不到 `ik_max_word` 而失败，集群变红。正确做法是写进 `Dockerfile` 让插件成为镜像的一部分。教训：任何对运行中容器做的手工修改都是一次性的。

### 10. RocketMQ 容器内网地址不可达

broker 注册到 namesrv 的是容器内网 IP，客户端拿到必然超时。必须在 `broker.conf` 里显式 `brokerIP1 = ${LAB_PUBLIC_HOST}`，该占位符由启动时 `envsubst` 渲染。Kafka 是同一类问题，`KAFKA_CFG_ADVERTISED_LISTENERS` 必须填公网地址。

### 11. RocketMQ 配置渲染写不进安装目录

渲染结果原本写到 `/home/rocketmq/conf/broker.conf`，重建容器后陷入重启循环：`Permission denied`。根因是**镜像里根本没有这个目录**，且容器以 uid 3000 运行无权创建，渲染到 `/tmp` 即可。

**这个坑藏了近一个月**：服务器上原先跑的是直接挂载 `broker.conf:ro` 的旧配置，与本仓库版本早已不同步，本地这套 envsubst 方案从未真正运行过，直到本次重建才暴露。教训是部署配置改完必须实跑一次——`docker compose config` 能解析，不代表容器起得来。

### 12. RocketMQ 版本与存储卷权限

5.3.1 在此环境启动即失败，换 4.9.7 正常。镜像以 `rocketmq` 用户运行，命名卷由 root 创建时无写权限，改用 bind mount 并授权后解决。

### 13. 顺序消息的 keys 丢失

`RocketMQTemplate.syncSendOrderly()` 会重建 Message 对象导致 `keys` 丢失，消费端取到 null 触发非空约束。改用原生 `producer.send(message, selector, shardingKey)` 保留 keys，消费端兜底：keys 为空时用 msgId。

### 14. TCC 的 error_message 非空约束

回滚时 `error_message` 为 null 违反 NOT NULL 约束，导致回滚本身失败。SQL 中用 `ifnull(..., '')` 兜底，Java 侧统一写空字符串。

### 15. Redisson 限流让四种算法退化成同一种

最初用 `RedissonClient.getRateLimiter()`，并写了 `algorithm == TOKEN_BUCKET ? OVERALL : OVERALL` 这样的分支——两个分支相同，实测四种算法放行数量**完全相同**，对比实验彻底失真。根因不是笔误：`RRateLimiter` 底层只有令牌桶一种实现，只有 `OVERALL` 与 `PER_CLIENT` 两种计数维度，无法表达滑动窗口和漏桶。

**解法**：用 Lua 在 Redis 上自行实现四种算法（`framework/limiter/impl/LuaRateLimiter`）。要点：时间一律用 `redis.call('TIME')` 取服务器时间（多实例各节点时钟必然有偏差）；滑动窗口用有序集合记录时间戳，需要额外序列号 key，否则同一毫秒内的请求会因成员名相同被覆盖；令牌桶与漏桶的浮点状态写入前要格式化，避免科学计数法写进 Redis 后读不出来。

**认知修正**：令牌桶与漏桶在持续请求下会略超 `limit`，这不是超额——它们限制的是平均速率而非窗口内瞬时总量。`limit=10`、窗口 6 秒、30 次请求打到远程 Redis 约耗时一到两秒，期间补充了几个额度，因此放行 11 个。

### 16. COLA 内部迁移没有 to 环节

外部迁移是 `from → to → on`，内部迁移却是 `within → on`，因为 `within` 已把源和目标都置成同一状态。第一版照着外部迁移习惯写成 `.within(X).to(X)`，编译直接报错。教训：链式 DSL 的方法签名要逐个确认，不同分支的链路长度未必相同。

### 17. 缓存回源失败会固化成"数据不存在"

回源失败时若照常写空值标记，一次下游故障就被固化成「数据不存在」，标记过期前所有请求都拿到错误答案。正确做法是回源失败绝不写标记；有逻辑过期的旧值就带 `stale` 标记返回，没有才明确报 1005。

---

## 十、编码规范（dong-standards）

| 规则 | 说明 |
|---|---|
| 注释 | 中文注释，写设计意图、原理与坑点，不复述代码 |
| 成员顺序 | Javadoc → 注解 → 声明，注解与类型声明之间不留空行 |
| 空行 | 文件末尾空行；方法与字段前后空行；方法体内不空行 |
| SQL | 关键字小写，DDL 先行（先建表再写实体与 Mapper），手写 `limit` 分页 |
| 分层 | controller / service + impl / mapper / entity / dto / enums；定时任务进 `task`、消息处理器进 `handler`、无状态工具进 `support` |
| 接口方法 | 一律 POST，查询参数进 JSON body，分页 DTO 继承 `PageQuery` |
| 命名 | XxxController、XxxService + XxxServiceImpl、XxxMapper；实体不带后缀 |
| 统一响应 | `Result.success(data)` / `Result.fail(code, message)`，错误码取自 `Constants` |
| 参数校验 | `@NotBlank` 即必填，可选参数只能用 `@Size`；拒绝取值越界，允许空结果 |
| 状态与时间 | 状态用枚举落库 int + TypeHandler；时间用 `datetime` + `LocalDateTime` |
| 事务与返回 | `@Transactional(rollbackFor = Exception.class)`；集合返回空集合而非 null |

正确性与降级优先于性能：宁可返回带标记的旧数据或明确报不可用，也不要为了"每次都返回数据"而掩盖故障。
