# dong-lab

中间件与分布式场景实验室。一个 Spring Boot 3 工程，把日常开发中容易只在理论上理解的中间件用法，全部变成可以运行、可以对比、可以用数据验证的代码。

技术栈：Spring Boot 3.4.5 / JDK 21 / MySQL 8 / Redis 7 / Redisson 3.45 / MyBatis（原生 XML）/ RocketMQ 4.9.7 / Kafka 3.7 / Elasticsearch 8.15（IK 分词）/ MongoDB 7 / MariaDB 10.11

---

## 目录

| 章节 | 内容 |
|---|---|
| [一、项目定位](#一项目定位) | 这不是脚手架，是一组对照实验 |
| [二、能力速览](#二能力速览) | 十个场景一张表看完 |
| [三、快速开始](#三快速开始) | 三步跑起来 |
| [四、目录结构与分层](#四目录结构与分层) | 限界上下文 + 传统分层 |
| [五、场景详解](#五场景详解) | 每个场景的问题、解法、验证命令 |
| [六、开关化设计](#六开关化设计) | 中间件按需开关 |
| [七、云服务器部署](#七云服务器部署) | 2 核 2G 下的按需启停 |
| [八、验证记录](#八验证记录) | 实测数据 |
| [九、踩过的坑](#九踩过的坑) | 真实问题与解法 |
| [十、编码规范](#十编码规范) | dong-standards |
| [专项文档](doc/crossborder-payment.md) | 跨境支付业务专项学习 |

---

## 一、项目定位

它不是脚手架，也不是业务系统，而是一组**可对照实验**。每个场景都回答三个问题：

1. **没有这个中间件时，问题长什么样**（错误现象 + 量化损失）
2. **用了之后，为什么能解决**（原理 + 关键代码）
3. **代价是什么**（性能、一致性、运维复杂度）

例如分布式锁，项目同时提供「不加锁」和「加 Redisson 锁」两个接口。实测在 8 线程 × 10 次并发自增下：

| 模式 | 期望值 | 实际值 | 丢失更新 | 耗时 |
|---|---|---|---|---|
| 不加锁 | 80 | 2 | 78 | 280ms |
| Redisson 锁 | 80 | 80 | 0 | 18.4s |

数字本身就在说明问题：锁换来正确性，代价是 65 倍的耗时。这类对照在项目中一共有三十余处。

---

## 二、能力速览

十个限界上下文，每个对应一类中间件能力与一个真实场景。

| 场景 | 接口前缀 | 核心中间件 | 关键能力 | 实测结论 |
|---|---|---|---|---|
| 多级缓存 | `/api/cache` | Caffeine + Redis | 穿透、击穿、雪崩、双写一致性 | 穿透防护 444ms → 102ms |
| Redis 经典 | `/api/classic` | Redis + Redisson | 排行榜、UV、签到、短链、GEO、延迟队列、发号器、限流、分布式锁 | 加锁零丢失，代价 65 倍耗时 |
| 秒杀 | `/api/seckill` | Redis Lua + MQ | 预扣库存、异步下单、售罄短路 | 10 库存 20 人抢，零超卖 |
| 抢红包 | `/api/red-packet` | Redis List | 二倍均值法、预分配 | 金额精确守恒，分毫不差 |
| 微博模型 | `/api/social` | Redis Set / ZSet | 关注关系、共同关注、推拉两种时间线 | 两种模式结果一致 |
| 搜索 | `/api/search` | Elasticsearch + IK | 中文分词、高亮、分面聚合 | 中文命中并高亮 |
| 分布式事务 | `/api/tcc` | MySQL | Try/Confirm/Cancel + 幂等、空回滚、悬挂 | 失败分支零残留 |
| 消息 | `/api/mq` | local / RocketMQ / Kafka | 顺序、延迟、批量、幂等 | 重复投递被拦截 |
| 文档 | `/api/doc` | MongoDB | 无 schema 日志 | 字段可随业务演进 |
| 多数据源 | `/api/replica` | MariaDB | 第二数据源、独立事务管理器 | 一致性检查通过 |
| 跨境支付 | `/api/crossborder` | MySQL + MQ + Redis | 幂等、锁汇、合规筛查、人工审核、账户冻结、异步清算、对账 | 金额精确到分，余额流水一致 |
| 订单状态机 | `/api/order` | MySQL + COLA StateMachine | 守卫、内部迁移、条件分支回退、乐观锁并发 | 16 线程抢推，仅 1 次成功 |

---

## 三、快速开始

本项目**不在本地运行任何中间件**，全部连接云服务器。启动前需要一份本机凭据文件。

### 1. 账号密码存在哪

全部凭据集中在一个文件：**`deploy/.env`**。它就躺在项目里，随时可以打开查看和修改。

```
deploy/
├── .env              ← 真实账号密码，本地可见，已被 git 忽略
├── .env.example      ← 模板，进版本库，只含变量名和占位值
├── setup-env.sh      ← 交互式生成 .env
└── print-env.sh      ← 打印可粘贴到服务器的命令
```

`.env` 是点开头的隐藏文件，macOS 访达默认不显示，两种办法查看：

```bash
cat deploy/.env                    # 终端查看
open -e deploy/.env                # 用文本编辑器打开
```

访达中按 `Cmd + Shift + .` 可切换显示隐藏文件。

**安全边界在 `.gitignore`**：`*.env` 被忽略、`!*.env.example` 放行，所以 `.env` 永远不会被提交。可以随时自查：

```bash
git check-ignore -v deploy/.env    # 有输出即表示已被忽略
git status --short                 # 不应出现 .env
```

若 `.env` 不存在，从模板复制后填入真实密码即可：

```bash
cp deploy/.env.example deploy/.env && chmod 600 deploy/.env && vi deploy/.env
```

变量含义见第七节。MongoDB 密码若含 `@` 要填 URL 编码后的值（`@` → `%40`）。

### 2. 准备远程数据库

首次部署时，云服务器的 MySQL 由 `deploy/initdb` 自动建表。如需手工执行：

```bash
mysql -h <host> -uroot -p < db/schema.sql
```

### 3. 启动

```bash
mvn spring-boot:run
```

默认端口 **8090**。

`application.yml` 通过 `spring.config.import` 自动读取 `deploy/.env`，**不需要手动 source**。

环境变量缺失时应用会**直接启动失败**并提示 `Could not resolve placeholder 'LAB_XXX'`，不会静默回落到本地地址。

### 4. 验证

```bash
curl http://127.0.0.1:8090/actuator/health
```

返回 `UP` 即成功。

### 5. 打开接口文档

应用启动成功后会自动打印可用地址，无需记忆：

```
------------------------------------------------------------
knife4j ui    http://127.0.0.1:8090/doc.html
swagger ui    http://127.0.0.1:8090/swagger-ui/index.html
swagger short http://127.0.0.1:8090/swagger-ui.html
openapi json  http://127.0.0.1:8090/v3/api-docs
actuator      http://127.0.0.1:8090/actuator/health
------------------------------------------------------------
```

在浏览器打开 `knife4j ui` 那一行即可，共 141 个接口，支持中文界面、搜索、在线调试与导出。

几个入口的区别：

| 地址 | 用途 |
|---|---|
| `/doc.html` | Knife4j 界面，中文、可搜索、支持导出，**推荐** |
| `/swagger-ui/index.html` | 原生 Swagger UI，作为备选 |
| `/swagger-ui.html` | 同上，自动跳转，便于记忆 |
| `/v3/api-docs` | OpenAPI 3.1 JSON，可导入 Apifox、Postman |

两者共用同一份 `/v3/api-docs` 数据，用哪个都不会有信息差。

**为什么这样组合**：Knife4j 的 starter 与其绑定的 springdoc 2.3.0 无法在 Spring Boot 3.4 上共存，而 springdoc 自带的 UI 又会注册 Spring 6.2 拒绝的路径模式。最终方案是引入 Knife4j 的纯静态资源包提供 `doc.html`，接口数据仍由 springdoc 2.9.0 生成，并移除 springdoc 中那个会注册非法路径的 bean。详见第九节。

---

## 四、目录结构与分层

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
│   ├── lock/                   分布式锁接口 + LockHandle，实现在 impl
│   ├── cache/                  多级缓存接口、失效总线、统计，实现在 impl
│   ├── limiter/                RateLimiter 接口与 @RateLimited 切面，实现在 impl
│   ├── bloom/                  Redisson 布隆过滤器
│   └── mq/                     MessageProducer 接口与 MqFacade 路由，实现在 impl
│
├── cache/                      多级缓存：穿透、击穿、雪崩、双写一致性
├── classic/                    Redis 经典场景：排行榜、UV、签到、短链、GEO、延迟队列、发号器
├── seckill/                    秒杀：预扣库存、Lua 原子脚本、异步下单、售罄短路
├── redpacket/                  抢红包：二倍均值法、预分配、原子抢
├── social/                     微博模型：关注关系、共同关注、推拉两种时间线
├── search/                     Elasticsearch：IK 分词、高亮、过滤、聚合
├── tcc/                        分布式事务 TCC：Try/Confirm/Cancel、恢复任务
├── mq/                         消息场景：顺序、延迟、幂等、批量
├── doc/                        MongoDB：无 schema 日志
├── replica/                    MariaDB：第二数据源、独立事务管理器
├── crossborder/                跨境支付：幂等、锁汇、合规筛查、人工审核、账户冻结、异步清算、对账
└── order/                      订单履约状态机：COLA 状态机、守卫、内部迁移、条件分支、乐观锁并发

src/main/resources/
├── application.yml             唯一配置文件，全部连接指向云服务器
└── mapper/**/*.xml             手写 SQL

db/schema.sql                   建表语句
deploy/                         Docker Compose 编排与启停脚本
```

### 配置文件

项目只有**一份** `application.yml`，没有按环境拆分的 profile 文件。

本地不运行任何中间件实例，所有连接一律指向云服务器，开发机只跑应用进程本身。

配置分两类内容：

| 类别 | 内容 |
|---|---|
| 与部署位置无关 | Jackson、MyBatis、Actuator、Springdoc、日志、端口、缓存策略、发号器、秒杀参数、各连接池与序列化器 |
| 通过环境变量注入 | 全部连接串与账号（`${LAB_PUBLIC_HOST}` 与 `${LAB_*}`）、中间件开关 |

环境专属的部分一律走环境变量，配置文件里不含任何明文凭据，也不含任何 `127.0.0.1` 地址。

文件顶部这行让配置自动读取 `deploy/.env`，启动前无需手动 `source`：

```yaml
spring:
  config:
    import: optional:file:./deploy/.env[.properties]
```

后缀 `[.properties]` 是必需的。Spring Boot 3.4.5 没有内置 dotenv 加载器，无法凭 `.env` 这个隐藏文件名判断格式；显式声明后，`KEY=VALUE` 的内容就能被 properties 加载器正确解析。

变量缺失时应用直接启动失败并报 `Could not resolve placeholder 'LAB_XXX'`，不会静默回落到本地——这样可以避免"以为连的是线上、实际连到本地脏数据"的问题。取值方式见第七节的 `.env` 配置。

Elasticsearch 与 MongoDB 的健康检查指示器是关闭的。它们是可选组件，一旦没启动，健康检查就会把整个应用标成 DOWN，而实际上主流程完全正常。关掉指示器后，只有真正影响主流程的 MySQL 与 Redis 才会决定 `/actuator/health` 的状态。

### 分层约定

| 目录 | 职责 | 禁止 |
|---|---|---|
| `controller` | 参数校验、调 service、封装 Result | 写业务逻辑 |
| `service` | 接口定义 | — |
| `service/impl` | 业务逻辑、事务边界 | 直接依赖具体中间件实现 |
| `task` | 定时任务，只做调度与编排 | 写业务逻辑 |
| `handler` | 消息处理器，消费后转调 service | 直接改状态或直接写库 |
| `support` | 无状态工具与进程内组件 | 持有业务状态、依赖 mapper |
| `mapper` | 数据访问 | 写业务逻辑 |
| `entity` | 与表一一对应 | 放业务方法 |
| `dto` | 请求 / 响应对象 | 与 entity 混用 |
| `enums` | 状态码，落库为 int | 用魔法数字 |

**接口与实现一律分包**：`service` 包只放接口，实现全部进 `service/impl`；`framework` 技术层同理，接口留在本包（`CacheStore`、`RateLimiter`、`MessageProducer`、`DistributedLockService`），实现进各自的 `impl` 子包。定时任务、消息处理器、无状态工具不属于业务服务，不进 `service` 包，分别归入 `task`、`handler`、`support`——否则 `service` 包里会混进一批没有接口的类，接口与实现的对应关系就不再可信。

---

## 五、场景详解

### 5.1 多级缓存（cache）

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

# 命中统计
curl http://127.0.0.1:8090/api/cache/lab/stats
```

实测 2000 次不存在的 id 查询，空值标记约 444ms，布隆过滤器约 102ms，相差 4 倍以上。

**关键实现**：`framework/cache/MultiLevelCache.java`

读路径：L1 Caffeine → L2 Redis → 数据库，逐层回填。
写路径：更新数据库 → 删除缓存 → 延迟 500ms 再删一次。第二次删除是为了清掉「读请求在更新提交前加载到旧值、之后又写了回去」的残留。

L1 的 TTL 上限被限制在 60 秒，且所有失效都会通过 Redis 发布订阅广播给其他节点。这是刻意的：本地缓存无法跨节点失效，只能缩短它的生命周期来兜底。

### 5.2 Redis 经典场景（classic）

| 场景 | 数据结构 | 接口 |
|---|---|---|
| 排行榜 | ZSet | `POST /api/classic/rank/submit?board=game&member=alice&score=100` |
| 排行榜查询 | ZSet | `GET /api/classic/rank/top?board=game&size=10` |
| 周榜结算 | ZSet | `POST /api/classic/rank/settle-weekly?board=game` |
| UV 统计 | HyperLogLog | `POST /api/classic/uv/record?page=home&visitorId=u1` |
| UV 区间合并 | HyperLogLog | `GET /api/classic/uv/range?page=home&from=2026-08-01&to=2026-08-07` |
| 签到日历 | Bitmap | `POST /api/classic/sign?userId=u1` |
| 连续签到 | Bitmap | `GET /api/classic/sign/streak?userId=u1` |
| 月签到日历 | Bitmap | `GET /api/classic/sign/calendar?userId=u1&month=2026-08` |
| 短链生成 | String + Snowflake | `POST /api/classic/short-link?url=https://example.com` |
| 短链跳转 | String | `GET /api/classic/short-link/s/{code}`（302 重定向） |
| 短链统计 | String | `GET /api/classic/short-link/hits?code=xxx` |
| 附近的人 | GEO | `GET /api/classic/geo/nearby?longitude=116.40&latitude=39.90&radiusKm=5` |
| 距离计算 | GEO | `GET /api/classic/geo/distance?first=a&second=b` |
| 延迟队列 | RDelayedQueue | `POST /api/classic/delay-queue/offer?payload=order-1&delaySeconds=5` |
| 发号器 | Snowflake / 号段 / INCR / UUID | `GET /api/classic/id?strategy=snowflake&count=1000` |
| 限流 | 四种算法 | `GET /api/classic/limiter/compare?limit=10&attempts=30&gapMillis=3500` |
| 分布式锁 | Redisson RLock | `GET /api/classic/lock/with-lock?threads=8&loops=10` |

**限流算法对比**：

```bash
curl 'http://127.0.0.1:8090/api/classic/limiter/compare?limit=10&windowSeconds=6&attempts=30&distributed=true&gapMillis=3500'
```

`gapMillis` 表示两轮突发之间的等待时间，是看出算法差异的关键。

只打一轮突发时，四种算法的放行数量几乎相同（窗口内都最多放行 `limit` 个），区分不出算法。真正的差异在**配额如何恢复**，因此要看第二轮：

| 参数 | fixed_window | sliding_window | token_bucket | leaky_bucket |
|---|---|---|---|---|
| 第一轮放行 | 10 | 10 | 11~12 | 10~12 |
| 间隔 3 秒后放行 | 0 | 0 | 6 | 6~7 |
| 间隔 3.5 秒后放行 | 10 | 0 | 7 | 7 |
| 间隔 5 秒后放行 | 10 | 10 | 10 | 10 |

（实测于远程 Redis，`limit=10`、窗口 6 秒、每轮 30 次请求，重复多次结果稳定）

第一轮的数值会有波动，因为令牌桶与漏桶在请求期间持续恢复配额，放行量取决于这 30 次请求实际耗时多久。间隔后的第二轮数值更稳定，也更适合用来对比。

几处值得留意：

- **固定窗口在 3.5 秒时已经放行 10 个**，说明它跨过窗口边界就一次性归零，而不是逐步恢复
- **滑动窗口在同一时刻仍是 0**，因为请求还没滑出窗口，它是最严格的
- **令牌桶与漏桶第一轮会略超 10**，这不是超额——它们限制的是平均速率而非窗口内瞬时总量，持续请求期间配额会按速率恢复

**为什么自己用 Lua 实现**：Redisson 的 `RRateLimiter` 底层只有令牌桶一种实现，只能选全局或按客户端计数，四种算法会退化成同一种行为。本项目改为在 Redis 上用 Lua 自行实现四种算法，对比才有意义。详见第九节。

**锁的对照实验**：

```bash
curl 'http://127.0.0.1:8090/api/classic/lock/without-lock?threads=8&loops=10'
curl 'http://127.0.0.1:8090/api/classic/lock/with-lock?threads=8&loops=10'
```

返回结果中区分了 `lockAcquired` 与 `lockTimedOut`，拿不到锁的线程不会被静默计入丢失。

**短链用 302 而非 301**：301 会被浏览器永久缓存，之后就不再经过服务端，点击统计会失效。

### 5.3 秒杀（seckill）

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

超时未支付的订单由定时任务取消并回滚库存，时长由 `lab.seckill.payment-timeout-minutes` 控制（默认 15 分钟）。

### 5.4 抢红包（redpacket）

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
curl "http://127.0.0.1:8090/api/red-packet/records?packetNo=$PN"
```

实测：10 人抢完，金额合计恰好 10000 分，分毫不差；第 11、12 人正确被拒。

### 5.5 微博模型（social）

**关注关系**用 Redis Set 存储，天然支持交集运算，共同关注就是一次 `SINTER`。

**Feed 流推拉两种模式都实现了**，可以对比：

| 模式 | 写操作 | 读操作 | 适用 |
|---|---|---|---|
| 推（写扩散） | 发一条，写给所有粉丝 | 直接读自己的时间线 | 粉丝少、读多 |
| 拉（读扩散） | 只写一份 | 拉取时聚合所有关注者 | 大 V、粉丝多 |

```bash
# 1 和 3 都关注 2
curl -X POST 'http://127.0.0.1:8090/api/social/follow?followerId=1&followeeId=2'
curl -X POST 'http://127.0.0.1:8090/api/social/follow?followerId=3&followeeId=2'

# 共同关注，交集为 2
curl 'http://127.0.0.1:8090/api/social/common-followees?firstUserId=1&secondUserId=3'

# 2 发一条动态
curl -X POST 'http://127.0.0.1:8090/api/social/feed?authorId=2&content=hello'

# 推模式：直接读准备好的时间线
curl 'http://127.0.0.1:8090/api/social/timeline/push?userId=1&size=10'

# 拉模式：读时聚合所有关注者
curl 'http://127.0.0.1:8090/api/social/timeline/pull?userId=1&pageNum=1&pageSize=10'
```

微博的实际做法是推拉结合：大 V 走拉，普通用户走推。

### 5.6 Elasticsearch（search）

**IK 中文分词**，索引映射由 `SearchIndexInitializer` 在启动时显式创建（`category` 为 keyword 以支持聚合，`name`/`description` 用 `ik_max_word` 索引、`ik_smart` 查询）。

```bash
# 从 MySQL 全量同步
curl -X POST http://127.0.0.1:8090/api/search/sync

# 中文检索，带高亮和分面聚合
curl -G http://127.0.0.1:8090/api/search --data-urlencode 'keyword=云服务器'
```

返回包含高亮片段 `<em>云</em><em>服务器</em>测试商品` 和分类分面统计。

### 5.7 分布式事务 TCC（tcc）

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

`TccRecoveryTask` 每 30 秒扫描一次处于 CONFIRMING 状态的事务，决定推进还是回滚，保证宕机后也能自愈。也可以手动触发：

```bash
curl -X POST http://127.0.0.1:8090/api/tcc/recover
```

### 5.8 消息（mq）

**抽象层设计**：业务代码只依赖 `MessageProducer` 接口，`MqFacade` 根据 `lab.mq.active` 路由到具体实现。切换 Kafka 和 RocketMQ 是改配置，不是改代码。

| 传输 | 说明 | 状态 |
|---|---|---|
| `local` | JVM 内总线，无需任何中间件即可跑通全部流程 | 默认，已验证 |
| `rocketmq` | 延迟消息（18 个固定等级）、顺序消息、集群消费 | 已部署并验证 |
| `kafka` | 顺序消息靠分区键，延迟消息靠「not before」头 + 消费端暂存 | 编排就绪，按需启动 |

**已验证的能力**（`RocketMqListener` 复用统一的 `MessageHandler`，与本地总线同一套业务代码）：

```bash
# 普通消息
curl -X POST 'http://127.0.0.1:8090/api/mq/send?key=k1' --data-urlencode 'payload={"seq":1}'

# 顺序消息，同一 shardingKey 进入同一队列，按序消费
curl -X POST 'http://127.0.0.1:8090/api/mq/send-ordered?key=o1&shardingKey=shard-1'

# 延迟消息
curl -X POST 'http://127.0.0.1:8090/api/mq/send-delayed?key=d1&delaySeconds=5'

# 批量消息
curl -X POST 'http://127.0.0.1:8090/api/mq/send-batch?keyPrefix=batch&count=20'

# 消费统计与投递日志
curl http://127.0.0.1:8090/api/mq/stats
curl http://127.0.0.1:8090/api/mq/logs
```

消费端幂等靠 `mq_message_log` 的唯一索引，重复投递只会入库一次，统计接口中的 `duplicated` 计数可以验证。

### 5.9 MongoDB（doc）

无 schema 日志存储。适合记录结构不固定、字段会随业务演进的数据。

```bash
curl -X POST http://127.0.0.1:8090/api/doc/operation-log -H 'Content-Type: application/json' \
  -d '{"bizType":"order","bizId":"1001","operator":"dong","action":"create","detail":{"amount":99}}'

curl 'http://127.0.0.1:8090/api/doc/operation-log?bizType=order&pageNum=1&pageSize=20'
curl 'http://127.0.0.1:8090/api/doc/operation-log/count'
```

### 5.10 MariaDB 第二数据源（replica）

独立的数据源、会话工厂和事务管理器，演示多数据源配置与跨库一致性观察。

```bash
curl -X POST 'http://127.0.0.1:8090/api/replica/accounts?userId=1&username=dong&balance=100000'
curl 'http://127.0.0.1:8090/api/replica/accounts/consistency?userId=1'
curl -X POST 'http://127.0.0.1:8090/api/replica/accounts/transfer?fromUserId=1&toUserId=2&amount=100'
```

---

### 5.11 跨境支付（crossborder）

> **专项学习文档**：[`doc/crossborder-payment.md`](doc/crossborder-payment.md)。从业务背景、领域概念讲到状态机与风控判定，含完整实操命令与实测数据。本节只做概要。

这是最贴近真实业务的一个场景。一笔跨境汇款从发起到到账要经过九个环节，每个环节都有对应的工程问题：

```
汇款申请 → 幂等校验 → 合规筛查 →（大额挂起 → 人工审核）→ 锁定汇率 → 扣款记账 → 异步清算 → 收款入账 → 对账核销
```

#### 5.11.1 业务场景讲解

| 环节 | 真实问题 | 本项目的做法 |
|---|---|---|
| 汇款申请 | 网络超时后重试导致重复汇款 | `idempotent_key` 唯一索引 + 分布式锁，重放返回原单 |
| 合规筛查 | 制裁名单命中必须拒绝，这是监管硬性要求 | 名单放 Redis Set（O(1) 匹配），四道检查逐条留痕 |
| 人工审核 | 大额交易合法但可疑，机器不敢直接放行 | 超过 5 万自动挂起 PENDING_REVIEW，合规人员放行或驳回，决策落库留痕 |
| 账户冻结 | 反洗钱调查期间账户不能继续交易 | 账户级冻结/解冻，操作原因与操作人落事件表，余额与流水完整保留 |
| 锁定汇率 | 汇率实时波动，不锁汇银行要承担敞口风险 | 报价带有效期，乐观锁锁定，过期作废 |
| 风控限额 | 日累计限额并发下会被突破 | Lua 脚本原子完成累加与判断，失败与驳回释放占用 |
| 扣款记账 | 扣款与记账必须原子，否则资金账实不符 | 同一本地事务，余额扣减带充足条件防负数 |
| 异步清算 | 渠道只有批量清算窗口，做不到实时 | RocketMQ 顺序消息推进，由补偿任务兜底 |
| 收款入账 | 重复消息导致重复入账 | 消费幂等 + 流水唯一索引兜底 |
| 对账核销 | 渠道回单与本地流水不一致 | 差异表记录长款短款，运营按类型处理 |

**为什么选本地事务加消息而不是 TCC**：扣款记账用本地事务保证资金账实相符；清算要经过外部渠道，渠道本身只支持异步批量，把它拉进强一致事务既做不到也没有必要，最终一致加对账兜底才是支付系统的实际做法。

**人工审核的业务闭环**：大额汇款挂起时资金分文未动，日限额已被占用——单子还活着，额度就该被占着。审核放行后补完主链路的后半段（锁汇 → 扣款 → 清算消息），与自动通过的单子殊途同归；审核驳回则进入终态并释放日限额，被驳回的金额不会永久占用客户当天额度。审核期间付款账户可能被反洗钱调查冻结，放行前必须重新校验账户可用性，不能信任挂起时的检查结果。

**实测**：1000 CNY 汇往 USD，CIPS 渠道（固定费 10 加万分之五），按锁定汇率 0.14006993 成交，收款方精确收到 140.07 USD，付款方扣款 1010.50 CNY，余额与流水差额为零。

**一个真实事故级别的坑**：入账逻辑最初写在消费者内部方法上，`this` 调用绕过了 Spring 事务代理，事务静默失效。并发到达的重复消息各自提交了加钱，流水唯一索引冲突却回滚不了已提交的余额变更，收款方余额被重复累加。修复方式是把账务操作拆到独立的 `CrossBorderLedgerService`，让事务真正经过代理生效。

#### 5.11.2 接口文档讲解

全部 45 个接口按六个 Controller 分组，响应统一用 `Result<T>` 包装，业务错误码见全局异常处理器。文档入口 `/doc.html`（Knife4j），可在线调试。

**汇款 `/api/crossborder/remittance`（10 个）**

| 接口 | 说明 |
|---|---|
| `POST /remittance` | 发起汇款，body 含幂等键、双方账号、金额、可选渠道与报价号 |
| `GET /remittance/{remittanceNo}` | 按汇款单号查询 |
| `GET /remittance/by-idempotent/{idempotentKey}` | 按幂等键查询，超时重试后确认是否已受理 |
| `GET /remittance?status=&pageNum=&pageSize=` | 分页查询，可按状态过滤 |
| `GET /remittance/pending-review` | 待人工审核的汇款单列表 |
| `POST /remittance/{remittanceNo}/review/approve` | 审核放行，body `{reviewer, note}`，继续锁汇扣款清算 |
| `POST /remittance/{remittanceNo}/review/reject` | 审核驳回，终态并释放日限额占用 |
| `GET /remittance/{remittanceNo}/compliance` | 该单的全量合规检查记录（四道自动 + 人工复核） |
| `GET /remittance/by-batch/{batchNo}` | 按清算批次查询单子 |
| `GET /remittance/runtime` | 运行时统计：各状态单量、幂等命中、审核计数、消息计数 |

**账户 `/api/crossborder`（11 个）**

| 接口 | 说明 |
|---|---|
| `POST /accounts` | 开户，kycLevel 决定可汇额度 |
| `GET /accounts/{accountNo}` | 查账户，含可用余额（余额减冻结） |
| `GET /accounts` | 全部账户 |
| `POST /accounts/{accountNo}/freeze?reason=&operator=` | 冻结账户，事件落库留痕 |
| `POST /accounts/{accountNo}/unfreeze?reason=&operator=` | 解冻账户，同样留痕 |
| `GET /accounts/{accountNo}/events` | 冻结/解冻事件历史，按时间正序 |
| `GET /accounts/{accountNo}/diff?initial=` | 校验余额与流水差额，应为 0 |
| `POST /sanction?ownerName=` | 加入制裁名单 |
| `DELETE /sanction?ownerName=` | 移出制裁名单 |
| `GET /sanction/count` | 名单大小 |

**汇率 `/api/crossborder/fx`（5 个）**：`POST /fx/quote` 询价、`GET /fx/{quoteNo}` 查报价、`GET /fx/available` 可用报价、`GET /fx/rate` 当前牌价、`POST /fx/expire` 批量作废过期报价。

**清算 `/api/crossborder/settlement`（7 个）**：`POST /settlement/batch` 建批次、按批次号查询、`POST /settlement/batch/{batchNo}/collect` 收集进批、`POST /settlement/batch/{batchNo}/settle` 批次清算入账、`POST /settlement/close-overdue` 关闭超时批次、`GET /settlement/recon` 对账入口、`GET /settlement/status` 状态分布。

**风控 `/api/crossborder/risk`（6 个）**：渠道路由试算 `GET /risk/route`、AML 客户画像、可疑名单、重置、日额度重置、汇率敞口 `GET /risk/fx-exposure`。

**对账 `/api/crossborder/recon`（6 个）**：`POST /recon/{batchNo}` 执行对账、渠道回单查询、对账报告、`POST /recon/diff/{id}` 处理单条差异、`POST /recon/{batchNo}/handle-all` 批量处理、`GET /recon/overview` 总览。

**典型调用序列**：

```bash
# 1. 双方开户（不同币种）
curl -X POST 'http://127.0.0.1:8090/api/crossborder/accounts' -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","country":"CN","currency":"CNY","balance":100000,"kycLevel":2}'
curl -X POST 'http://127.0.0.1:8090/api/crossborder/accounts' -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","country":"US","currency":"USD","balance":0,"kycLevel":2}'

# 2. 询价并锁汇（也可让汇款接口自动询价）
curl -X POST 'http://127.0.0.1:8090/api/crossborder/fx/quote?sourceCurrency=CNY&targetCurrency=USD&validSeconds=300'

# 3. 发起汇款。注意开户默认单笔限额是 5 万，与人工审核阈值相同，
#    因此默认账户汇 5 万以上会被限额直接拒绝；要走到人工审核分支，
#    开户时需把 singleLimit 提到阈值之上，详见专项文档第 6.1 节
curl -X POST http://127.0.0.1:8090/api/crossborder/remittance -H 'Content-Type: application/json' \
  -d '{"idempotentKey":"unique-key-1","payerAccountNo":"CB...","payeeAccountNo":"CB...","sourceAmount":60000}'

# 4. 人工审核闭环
curl http://127.0.0.1:8090/api/crossborder/remittance/pending-review
curl -X POST 'http://127.0.0.1:8090/api/crossborder/remittance/RM.../review/approve' \
  -H 'Content-Type: application/json' -d '{"reviewer":"compliance-dong","note":"material verified"}'

# 5. 冻结可疑账户（反洗钱调查）
curl -X POST 'http://127.0.0.1:8090/api/crossborder/accounts/CB.../freeze?reason=aml investigation&operator=risk-team'
curl 'http://127.0.0.1:8090/api/crossborder/accounts/CB.../events'

# 6. 运行时观察
curl 'http://127.0.0.1:8090/api/crossborder/remittance/runtime'
```

#### 5.11.3 技术场景讲解

| 技术问题 | 场景 | 解法 |
|---|---|---|
| 接口幂等 | 客户端超时重试 | 先查幂等键 → 分布式锁内双查 → 唯一索引兜底，重放一律返回原单 |
| 状态机并发 | 消息重复消费、双审核员同时操作 | `update ... where status=期望 and version=当前` 乐观锁，抢占失败方报冲突或幂等返回 |
| 限额原子性 | 并发汇款突破日累计限额 | Lua 脚本内完成「累加 + 判断」，失败与驳回路径释放占用，杜绝额度泄漏 |
| 事务边界 | 消息先于事务提交发出 | `TransactionSynchronization.afterCommit` 注册回调，提交成功才发送 |
| 事务代理失效 | 同类内部调用绕过代理 | 账务操作独立成 `CrossBorderLedgerService` bean，事务真正生效 |
| 失败补偿 | 消息发不出去但款已扣 | 留下 FUNDS_DEBITED 单子，定时任务扫描补偿推进，资金在本地不回滚 |
| 留痕不可篡改 | 监管要求决策可追溯 | 合规记录与账户事件表只增不改，无更新接口 |
| 资金自检 | 记账遗漏或重复 | 贷方减借方反推余额与实际比对，diff 接口随时可查 |

**审核放行的并发设计**：两层防护。第一层用乐观锁把状态从 PENDING_REVIEW 抢占为 QUOTE_LOCKED——两个审核员同时点放行，只有一个能抢到，另一个报冲突。第二层是抢占后的写入天然无竞争，因为失败方已退出。锁汇或扣款中途失败会把状态回退到 PENDING_REVIEW，单子可重新审核，不会卡死在中间状态；回退而不是直接判死，是因为失败原因多是报价过期或余额不足，补齐后重审即可。

**账户冻结的幂等语义**：条件更新 `update ... set status=冻结 where status=激活`，重复冻结第二次匹配不到行，直接报冲突而不是把事件记录写重。状态变更与事件落库在同一事务，不会出现「冻了账户却没留痕」。

### 5.12 订单履约状态机（order）

全项目唯一引入第三方状态机组件的场景，用 COLA StateMachine 把履约规则显式化。状态只能由事件推进，接口层不提供任何直接改状态的入口——绕过状态机的口子一旦开了一个，规则迟早会被绕过。

**七种状态**：待支付 → 待发货 → 待收货 → 已完成，外加已取消、退款中、已退款。
**九种事件**：支付、取消、超时、发货、确认收货、催单、申请退款、退款成功、退款失败。

```bash
# 1. 创建订单，初始停在待支付
curl -X POST http://127.0.0.1:8090/api/order -H 'Content-Type: application/json' \
  -d '{"userId":1001,"productName":"机械键盘","quantity":1,"payAmount":399.00}'

# 2. 当前状态能触发哪些事件 → ["PAY","CANCEL","TIMEOUT"]
curl http://127.0.0.1:8090/api/order/TO.../available-events

# 3. 待支付直接发货，被状态机拦下 → order TO... cannot handle SHIP
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"SHIP","trackingNo":"SF001"}'

# 4. 支付不传流水号，被守卫拦下 → guard rejected event PAY from WAIT_PAY
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"PAY"}'

# 5. 正常链路：支付 → 发货 → 确认收货
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"PAY","payNo":"PAY20260905001"}'
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"SHIP","trackingNo":"SF20260905001"}'
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"RECEIVE"}'
```

**催单是内部迁移**：状态不变，只累加催单次数。实测触发后 `status` 仍是 `WAIT_SHIP`，`urgeCount` 从 0 变 1，而 `version` 纹丝不动——这正是内部迁移与外部迁移的区别。

**退款失败走条件分支**：同一个事件配两条迁移，靠守卫分流，退回发起退款前的状态而不是固定退回某一个。COLA 要求这种情况下每条迁移都必须带 `when`，否则装配期直接报错，这个约束反而挡住了歧义配置。

```bash
# 待收货 → 退款中 → 退款失败，退回待收货（而不是固定退回待发货）
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"APPLY_REFUND","refundAmount":399.00}'
curl -X POST http://127.0.0.1:8090/api/order/TO.../events -H 'Content-Type: application/json' \
  -d '{"event":"REFUND_FAIL","reason":"bank rejected"}'
```

**并发实验**：多个线程同时对同一张订单发货，对比带不带乐观锁的差别。

```bash
curl -X POST 'http://127.0.0.1:8090/api/order/benchmark?threads=16&mode=cas'
# → successCount=1      blockedCount=15    finalVersion=2   跑 5 轮，结果完全一致

curl -X POST 'http://127.0.0.1:8090/api/order/benchmark?threads=16&mode=none'
# → successCount=13~16  blockedCount=0~3   finalVersion=1   跑 5 轮，每轮都不一样
```

16 线程并发推进各跑 5 轮的实测：带乐观锁时 `successCount` 恒为 1、`finalVersion` 恒为 2，结果完全可预期；不带时 `successCount` 在 13 到 16 之间浮动，而 `finalVersion` 始终是 1——这些「成功」只是互相覆盖，连谁先谁后都无从追溯。

有意思的是 none 模式并非每次都是 16。读到旧状态的线程会重复推进，而读到新状态的线程会被状态机拦下（待收货不能再发货），浮动的幅度取决于这两拨线程的时序。**两道防线拦的本就不是同一类问题**：状态机拦非法跃迁，乐观锁拦并发覆盖。

**两个认知要点**：

- **状态机管不了并发**。COLA 状态机是无状态的，只回答「从某状态收到某事件该去哪」，不持有当前状态，因此可以被所有线程共享。真正的并发安全靠落库时的 `where status=期望 and version=当前`。两者职责不同，缺一不可：少了状态机，非法跃迁能绕过；少了乐观锁，两个线程能同时推进同一个订单。
- **被拒绝不等于没发生**。`fireEvent` 在迁移被拒时返回的是原状态，与内部迁移的返回值完全一样，光看返回值区分不了「被拦下」和「原地打转」。因此每个 action 都往上下文写 `accepted` 标记，只有 action 真被执行过才算通过。

---
## 六、开关化设计

中间件开关集中在唯一的 `application.yml` 中。云服务器上默认全部启用；只在本地验证某项功能时，用命令行参数临时关掉不需要的组件，不改动配置文件：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--lab.mariadb.enabled=false --lab.rocketmq.enabled=false"
```

常用开关：

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

## 七、云服务器部署

服务器为 2 核 2G，物理上无法同时运行全部中间件，因此采用**按需启停**的容器编排。

### 目录

```
/opt/dong-lab/
├── docker-compose.yml    编排文件，含内存限制与健康检查
├── .env                  密码与公网地址（不进版本库，由 setup-env.sh 生成）
├── .env.example          变量模板，进版本库
├── setup-env.sh          服务器上交互式生成 .env
├── print-env.sh          本地执行，打印可粘贴到服务器的命令
├── initdb/               主库建表 SQL，首次启动自动执行
├── initdb-replica/       从库建表 SQL
├── elasticsearch/        含 IK 插件的 Dockerfile
├── rocketmq/             broker.conf 模板，含 ${LAB_PUBLIC_HOST} 占位符
└── lab.sh                启停脚本
```

### 首次部署：配置 .env

仓库不保存密码。部署前用脚本生成 `.env`，密码交互式输入、不回显，生成的文件权限为 600 且已被 git 忽略：

```bash
cd /opt/dong-lab
./setup-env.sh
```

脚本会逐项提示，直接回车接受默认值。

若服务器无法直接拿到本仓库的脚本（例如只允许粘贴命令、不能传文件），可在**本地**运行 `print-env.sh`，它会打印出一段可直接粘贴到服务器执行的命令：

```bash
./print-env.sh                      # 本地执行，密码不回显、不落盘
```

把输出整段复制到服务器执行即可生成同样的 `.env`。MongoDB 密码会自动做 URL 编码（`@` → `%40`），无需手工处理。

也可以手工从模板复制后编辑：

```bash
cp .env.example .env && chmod 600 .env && vi .env
```

需要填写的变量：

| 变量 | 说明 |
|---|---|
| `LAB_PUBLIC_HOST` | 服务器公网 IP 或域名，RocketMQ 与 Kafka 对外声明的地址 |
| `LAB_MYSQL_USERNAME` / `LAB_MYSQL_PASSWORD` | MySQL 账号密码 |
| `LAB_REDIS_PASSWORD` | Redis 密码 |
| `LAB_MARIADB_USERNAME` / `LAB_MARIADB_PASSWORD` | MariaDB 账号密码 |
| `LAB_ES_PASSWORD` | Elasticsearch 的 `elastic` 用户密码 |
| `LAB_MONGO_USERNAME` / `LAB_MONGO_PASSWORD` | MongoDB 账号密码 |

变量缺失时 compose 会直接拒绝启动并提示缺哪个，不会带着空密码跑起来。

注意 MongoDB 密码若含 `@`，此处要填 **URL 编码后**的值（`@` → `%40`），原因见第九节第 12 条。

### 服务清单

| 服务 | 端口 | 镜像 | profile |
|---|---|---|---|
| MySQL | 3306 | `mysql:8.0` | core |
| Redis | 6379 | `redis:7.2-alpine` | core |
| RocketMQ | 9876 / 10911 | `apache/rocketmq:4.9.7` | mq |
| Kafka | 9092 | `bitnami/kafka:3.7` | kafka |
| Elasticsearch | 9200 | `lab-elasticsearch-ik:8.15.3` | search |
| MongoDB | 27017 | `mongo:7.0` | doc |
| MariaDB | 3307 | `mariadb:10.11` | replica |

### 使用

```bash
lab.sh core up      # MySQL + Redis，约 850m
lab.sh mq up        # RocketMQ（namesrv + broker），约 700m
lab.sh kafka up     # Kafka（KRaft，按需拉镜像），约 800m
lab.sh search up    # Elasticsearch（含 IK），约 900m
lab.sh doc up       # MongoDB，约 520m
lab.sh replica up   # MariaDB，约 390m
lab.sh full up      # 全部，2G 内存下不建议

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
mvn spring-boot:run
```

已验证的全部链路：MySQL、Redis（Redisson）、MariaDB、MongoDB、Elasticsearch（含 IK 分词）、RocketMQ。

由于配置默认启用全部中间件，若云端只起了一部分，需要把未启动的组件临时关掉，否则应用会卡在连接失败上：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--lab.mongodb.enabled=false --lab.elasticsearch.enabled=false --lab.rocketmq.enabled=false"
```

### 内存是硬约束

启动时请遵守按需原则，用完即停：

```bash
lab.sh search down     # 先停 ES
lab.sh doc down        # 再停 MongoDB
lab.sh mq up           # 然后启动消息中间件
```

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
| ES 集群 | IK 映射 | category 为 keyword，name/description 用 IK |
| 推拉时间线 | 关注 2 人 + 动态 | 两种模式结果一致，未关注者不出现 |
| 多数据源 | MariaDB 独立事务 | 创建、转账、一致性检查全部正常 |
| 错误处理 | 重复创建账户 | 返回 1002 业务错误，不泄露数据库细节 |

共 25 项端到端接口验证，全部通过。

---

## 九、踩过的坑

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

ES 的 bulk 接口即使单条失败，HTTP 也返回 200，必须检查响应体的 `errors()` 字段并逐条读取 `item.error().reason()`。加上检查后，日期格式不匹配的问题立刻暴露出来——在此之前它只是让所有文档都写不进去，接口却返回成功。

### 8. Spring Data 反射在 JDK 21 下的限制

用 `indexOps.createMapping()` 生成映射时，Spring Data 反射访问 `BigDecimal` 内部字段，被 JDK 21 模块系统拒绝（`InaccessibleObjectException`）。除非加 `--add-opens` 参数，否则要用原生客户端显式定义映射。

### 9. 多数据源导致 Mapper 扫描失效

开启 MariaDB 后，其 `@MapperScan` 抑制了 MyBatis 的自动扫描，主库 Mapper 全部失效。需要显式声明主库的数据源、`sqlSessionFactory` 和事务管理器，并加 `@Primary`。

### 10. ES 配置位置写错

地址写在 `spring.data.elasticsearch.uris`，但代码和 Spring Boot 健康检查读的是 `spring.elasticsearch.uris`（默认 localhost:9200）。健康检查因此一直报连接拒绝。

### 11. springdoc 与 Spring Framework 6.2

springdoc 的 UI 配置会注册 `/swagger-ui/**/*index.html` 这类资源模式，Spring Framework 6.2 的路径解析器拒绝它（`No more pattern data allowed after ** pattern element`），**直接导致启动失败**。

注册这段逻辑的是 `SwaggerConfig` 中的一个 bean。直接排除整个 `SwaggerConfig` 会连带丢掉 `/v3/api-docs/swagger-config` 端点，而 Knife4j 的界面依赖它。所以只移除那一个 bean：

```java
@Bean
public static BeanDefinitionRegistryPostProcessor springdocResourceConfigurerRemover() {
    return registry -> {
        if (registry.containsBeanDefinition("swaggerWebMvcConfigurer")) {
            registry.removeBeanDefinition("swaggerWebMvcConfigurer");
        }
    };
}
```

用的是 `BeanDefinitionRegistryPostProcessor` 而不是 `BeanFactoryPostProcessor`，因为 `removeBeanDefinition` 属于 `BeanDefinitionRegistry`，而 `ConfigurableListableBeanFactory` 并没有这个方法。

### 12. Knife4j starter 与新版 springdoc 无法共存

想用 Knife4j 的 `doc.html`，直觉是引入 `knife4j-openapi3-jakarta-spring-boot-starter`。实测走不通，两头堵死：

| 组合 | 结果 |
|---|---|
| starter 自带的 springdoc 2.3.0 | `NoSuchMethodError: ControllerAdviceBean.<init>(Object)`，该构造函数在 Spring 6.2 已移除 |
| 强制覆盖为 springdoc 2.9.0 | `NoSuchMethodError: SpringDocConfigProperties.getGroupConfigs()`，Knife4j 按 `List` 编译，2.4.0 起 springdoc 改成了 `Set` |

且 springdoc 2.4.0 之后就都是 `Set`，只有 2.3.0 是 `List`，所以不存在两者都满足的版本。

**解法**：`knife4j-openapi3-ui` 是**纯静态资源包**（不含任何 class），只引入它即可 —— `doc.html` 页面由它提供，接口数据仍由 springdoc 2.9.0 生成，Java 层不再有交集。

代价是失去 Knife4j 的后端增强（文档管理、全局参数等），核心的接口展示与在线调试不受影响。

### 13. 自建 Swagger 页面的版本号耦合

`static/swagger-ui/index.html` 里写死了 webjar 版本 `5.32.11`。升级 springdoc 时其传递依赖的 swagger-ui 版本可能变化，导致静态资源 404。`SwaggerUiConfig` 启动时会校验该版本是否存在，缺失则在日志中告警，按提示修改页面里的版本号即可。

页面里直接把 `url` 写成本项目的 `/v3/api-docs`，不依赖 springdoc 的 `SwaggerIndexTransformer`（webjar 自带的 initializer 默认指向 petstore 示例）。

### 14. 密码中的特殊字符

MongoDB 密码若含 `@`（例如 `P@ssw0rd`），在连接 URI 中必须编码为 `%40`（`P%40ssw0rd`），否则 URI 被解析成两个 `@`，主机与认证信息错位，连接直接失败。

现在密码统一放在 `.env` 的 `LAB_MONGO_PASSWORD`，**填编码后的值**。

### 15. IK 插件在容器重建后丢失

最初用 `docker exec` 在容器内手工安装 IK 插件，能正常工作。但 `docker compose down` 再 `up` 后容器被重建，插件消失，索引创建因找不到 `ik_max_word` 分析器而失败，集群状态变红。

正确做法是写进 `Dockerfile`，让插件成为镜像的一部分：

```dockerfile
FROM docker.elastic.co/elasticsearch/elasticsearch:8.15.3
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.15.3
```

教训：任何对运行中的容器做的手工修改都是一次性的，必须固化到镜像里。

### 16. RocketMQ 容器内网地址不可达

broker 启动后注册到 namesrv 的是容器内网 IP（`172.18.0.5`），客户端拿到这个地址去连，必然超时。必须在 `broker.conf` 里显式声明对外地址：

```properties
brokerIP1 = ${LAB_PUBLIC_HOST}
```

该占位符由容器启动时用 `envsubst` 渲染，值取自 `.env`。

Kafka 是同一类问题，`KAFKA_CFG_ADVERTISED_LISTENERS` 必须填公网地址，同样取自 `LAB_PUBLIC_HOST`。

### 17. RocketMQ 版本与存储卷权限

5.3.1 版本在此环境启动即失败，换 4.9.7 正常。此外镜像以 `rocketmq` 用户运行，命名卷由 root 创建时无写权限，改用 bind mount 并授权后解决。

### 18. 顺序消息的 keys 丢失

`RocketMQTemplate.syncSendOrderly()` 会重建 Message 对象，导致设置的 `keys` 丢失，消费端取到 null，入库时触发非空约束。改用原生 `producer.send(message, selector, shardingKey)` 保留 keys，同时消费端做兜底：keys 为空时用 msgId。

### 19. TCC 的 error_message 非空约束

事务回滚时 `error_message` 为 null，违反数据库 NOT NULL 约束，导致回滚本身失败。在 SQL 中用 `ifnull(..., '')` 兜底，Java 侧统一写空字符串。

### 20. Redisson 限流让四种算法退化成同一种

最初用 `RedissonClient.getRateLimiter()` 实现分布式限流，并写了这样的分支：

```java
return algorithm == RateLimitAlgorithm.TOKEN_BUCKET ? RateType.OVERALL : RateType.OVERALL;
```

两个分支都是 `OVERALL`，实测四种算法在分布式模式下放行数量**完全相同**——对比实验彻底失真。

根因不是笔误：Redisson 的 `RRateLimiter` 底层只有令牌桶一种实现，只有 `RateType.OVERALL` 与 `RateType.PER_CLIENT` 两种计数维度，无法表达滑动窗口和漏桶。补上分支也无从下手。

**解法**：用 Lua 在 Redis 上自行实现四种算法（见 `framework/limiter/impl/LuaRateLimiter`）。

几个实现要点：

- 时间一律用 `redis.call('TIME')` 取服务器时间。多实例部署时各节点时钟必然有偏差，用应用本地时间会让窗口边界不一致
- 滑动窗口用有序集合记录时间戳，需要额外的序列号 key，否则同一毫秒内的请求会因成员名相同而被覆盖
- 令牌桶与漏桶的浮点状态写入前要格式化，避免科学计数法写进 Redis 后读不出来

**另一个认知修正**：令牌桶与漏桶在持续请求下会略超 `limit`，这不是超额。它们限制的是平均速率而非窗口内瞬时总量，请求期间配额本就按速率恢复。实测 `limit=10`、窗口 6 秒、30 次请求打到远程 Redis 约耗时一到两秒，期间补充了几个额度，因此放行 11 个。

**对比实验的设计要点**：只打一轮突发区分不出算法，四种都会放行 `limit` 个。差异体现在配额如何恢复，所以接口提供了 `gapMillis` 参数打两轮，第二轮的结果才有区分度（见 5.2 节的实测表）。

### 21. 新增上下文的 Mapper 忘了登记

新建 `order` 模块后应用启动失败，报 `No qualifying bean of type 'TradeOrderMapper' available`。项目用了多数据源，主库的 Mapper 必须在 `config/PrimaryMybatisConfig` 的 `@MapperScan(basePackages = ...)` 里逐个登记，只在接口上加 `@Mapper` 不够——自动扫描会把它注册到默认的 SessionFactory，与主数据源指定的那一个不是同一个。

解法是在 `basePackages` 里补上 `com.dong.lab.order.mapper`。以后每加一个走主库的上下文，都要同步这一处。

### 22. COLA 内部迁移没有 to 环节

外部迁移的链式调用是 `from → to → on`，内部迁移却是 `within → on`，中间没有 `to`，因为 `within` 已经把源和目标都置成了同一个状态，`To` 接口上只有 `on(E)`。

第一版照着外部迁移的习惯写成 `.within(X).to(X)`，编译直接报找不到 `to`。教训是链式 DSL 的方法签名要逐个确认，不同分支的链路长度未必相同。

---

## 十、编码规范（dong-standards）

本项目严格遵循 `dong-standards` 规范，要点如下：

| 规则 | 说明 |
|---|---|
| 注释 | 业务模块默认禁止任何注释，代码应自解释；**本学习模块例外**，允许并鼓励中文注释，写设计意图与原理，不复述代码。详见规范第 1 条例外条款 |
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
