# 日志检索模块说明

`com.dong.logging`，接口前缀 `/api/logs`，页面 `/logs/index.html`。

把 ELK 的三件事收进同一个模块：采集（Logback → 有界缓冲 → ES）、存储（按天索引 + 别名）、检索与可视化。
这不是「接一个日志 SDK」，而是把每一环的取舍都做成可以观察、可以调参数的实验。

---

## 一、模块概览

```
打日志的线程                Spring 管理的刷写线程
     │                              │
log.xyz() ──► ElasticsearchLogAppender ──► LogEventBuffer（有界）──► LogShipperTask ──► ES bulk
                                              │                                          │
落盘文件 ──► LogFileImportService（行数位点续传）──────────────────────────────────────────┘
                                                                                          │
页面 /logs/index.html ──► LogController ──► LogSearchService ──► 读别名 dong_lab_log ◄──────┘
```

| 组件 | 位置 | 职责 |
|---|---|---|
| `ElasticsearchLogAppender` | `support` | 把 `ILoggingEvent` 转成文档入缓冲，只做这一件极轻的事 |
| `LogEventBuffer` | `support` | 有界队列，高水位降级，统计收发与丢弃 |
| `LogShipperTask` | `task` | 定时把缓冲批量刷进 ES，失败退避 |
| `LogRetentionTask` | `task` | 按保留天数整块删除过期索引 |
| `LogFileImportService` | `service` | 解析落盘文件并回填，按行数位点续传 |
| `LogIngestService` | `service` | 索引就绪、批量写入、刷新、清理、管道状态 |
| `LogSearchService` | `service` | 检索、上下文、仪表盘聚合 |
| `LogSampleService` | `service` | 生成样本日志，走完整采集链路 |
| `LogTraceFilter` | `web` | 每个请求注入 traceId 到 MDC，并回写 `X-Trace-Id` |
| `LogIndexNaming` | `config` | 读别名、按天索引名、状态索引名 |

**为什么 appender 不直接写 ES**：写入是同步网络 IO，放在打日志的线程里做，一次慢查询就把业务线程一起拖住。
更致命的是 ES 客户端自己也会打日志，同步写会形成「打日志 → 写 ES → 打日志」的递归。

---

## 二、业务场景

### 2.1 采集

| 问题 | 现象 | 这里的处理 |
|---|---|---|
| 日志把应用拖垮 | 同步写 ES 拖慢所有请求 | 只入队，网络 IO 交给独立刷写任务 |
| 采集自激 | ES 客户端日志触发又一次写入 | 按 logger 前缀挡掉 `com.dong.logging` / `org.elasticsearch` / `co.elastic.clients` / `org.apache.http` |
| 日志洪峰撑爆内存 | 缓冲无限增长 | 有界队列（默认 1 万），满了直接丢 |
| 洪峰里丢了关键日志 | ERROR 和 DEBUG 抢同一份配额 | 超过 80% 水位只保留 WARN 及以上 |
| 故障期间持续报错 | 每秒重试建索引 | 失败后退避 10 秒，期间快速失败 |

丢日志是被允许的行为，但必须是**可观测的丢弃**：`/api/logs/stats` 里能看到收到、写入、丢弃、
以及其中因高水位降级各是多少。看不到数字的丢弃才叫事故。

### 2.2 补齐

实时采集覆盖不到三类日志：应用启动之前的、上一次进程来不及刷出去的、ES 短暂不可用时丢掉的。
这些都靠 `/api/logs/import` 从落盘文件补。

- **位点按行数而不是字节**：字节偏移会在多字节字符与换行符宽度上漂移，漂移之后就从半行开始解析，产出一条谁也看不懂的日志。
- **位点存 ES 而不是本地文件**：位点是跨重启、跨实例共享的状态，放本地文件等于换台机器就重导一遍。
- **只导主日志与它轮转出来的 `.N.log.gz`**：error / warn / debug 那几个文件是主日志的子集，一起导就是成倍重复。
- **超出保留窗口的整目录跳过**：轮转目录名就是轮转日期，早于窗口的目录不必读。不做这一步，十几天的历史要靠几十次调用才能走到能导的那部分。
- **按日志时间归档到各自那天的索引**：一律写今天的话，「保留 N 天」会把整段历史一次性判死。

### 2.3 检索与可视化

| 视角 | 接口 | 说明 |
|---|---|---|
| 翻日志 | `/search` | 关键字进 must 打分，级别 / logger 前缀 / traceId / 时间范围进 filter |
| 还原现场 | `/context` | 取某条前后各 N 条，定义是时间上的邻近而不是分页偏移 |
| 看健康度 | `/overview` | 级别分布、时间直方图（堆叠到级别）、Top logger、Top 异常类型 |
| 看管道 | `/stats` | 缓冲积压、丢弃、写入、索引清单、最后一次错误 |

检索与仪表盘共用同一套过滤条件，图上看到的数字与列表里查到的条数永远一致。

---

## 三、技术场景

### 3.1 按天索引与读别名

```
dong_lab_log                   读别名，指向全部按天索引，检索只认它
dong_lab_log_2026_10_27        今天的真实索引，写入目标由日志时间算出
dong_lab_log_meta              回填位点，不参与检索
```

按天切索引的意义在清理这一刻兑现：过期是「删几个索引」，而不是 `delete_by_query` 逐条标记再等段合并——
后者代价高一个数量级，而且删完磁盘也不会立刻还回来。

这里**没有写别名**：写入目标由日期算出来是确定的，写别名只在多个索引共享一个写入口（按大小 rollover）时才是必需的。

跨天时 `LogIngestServiceImpl.ensureWriteIndex()` 自动建新索引并挂上读别名，应用无感知。

### 3.2 索引映射

```
timestamp      date     strict_date_optional_time||epoch_millis
level          keyword
logger         keyword
thread         keyword
message        text     ik_max_word 写入 / ik_smart 查询
exceptionType  keyword
exception      text     ik_max_word 写入 / ik_smart 查询
traceId        keyword
host           keyword
app            keyword
```

字段类型逐个写死，一个都不交给动态推断：字符串默认会被推断成 text，而 text 没有 doc_values，
对它做 terms 聚合会直接报错，且改不回来。

刷新间隔设成 5 秒：日志写入是持续的高吞吐动作，每写一次就强制刷新会把段合并压垮。
一次性动作（生成样本、回填）结束后才显式 `refresh()`，让结果立刻可见。

### 3.3 三条容易被忽略的细节

1. **appender 用 `UnsynchronizedAppenderBase` 而不是 `AppenderBase`**：后者在 `doAppend` 上加了 synchronized，
   所有线程打日志都要串行过这把锁。这里的共享状态只有一个线程安全的队列，同步纯属白付代价。
2. **appender 方法体整体包 try/catch**：appender 抛异常会顺着 `log.xyz()` 一路抛回业务代码，
   一个日志组件把业务流程打断是不可接受的。
3. **MDC 必须在 finally 里清**：线程是复用的。不清的话，下一次请求会带着上一个人的 traceId——
   数据没丢、也没报错，只是两条不同请求的日志被串成了一条，这是最难发现的一类错误。

---

## 四、接口文档

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/logs/search` | 分页检索，带正文与堆栈高亮 |
| POST | `/api/logs/overview` | 仪表盘：级别分布、时间直方图、Top logger、Top 异常 |
| POST | `/api/logs/context` | 某条日志的前后各 N 条 |
| POST | `/api/logs/stats` | 采集管道状态 |
| POST | `/api/logs/generate` | 生成样本日志（走完整采集链路） |
| POST | `/api/logs/import` | 从落盘文件回填，按位点续传 |
| POST | `/api/logs/retention` | 清理超出保留天数的索引 |

检索请求 `LogSearchRequest` 继承 `PageQuery`：

| 字段 | 类型 | 说明 |
|---|---|---|
| keyword | string | 走 multi_match：`message^3`、`exception`、`traceId^2` |
| levels | string[] | 级别多选，服务层统一转大写 |
| loggerPrefix | string | 走 prefix 查询，按包名收窄 |
| thread / traceId / host | string | 精确匹配 |
| fromMillis / toMillis | long | 毫秒时间戳，含边界 |

错误码：`1000` 参数非法（含 from > to、from+size 超一万条窗口）、`1001` 日志不存在（上下文查询）、
`1004` 模块未启用、`1005` ES 不可用。索引还没建时检索返回空结果而不是报错。

---

## 五、数据模型

本模块不建表。所有状态都在 ES 里：

| 索引 / 文档 | 用途 |
|---|---|
| `dong_lab_log_yyyy_MM_dd` | 日志文档，字段见 3.2 |
| `dong_lab_log_meta` | 回填位点：`path`（同时是文档 id）、`lines`、`size`、`done`、`updatedAt` |

位点的 `size` 用来识别文件被截断或轮转过——变小了就说明现在这个文件已经不是当初那个，必须从头再来。

---

## 六、本地验证

```bash
mvn spring-boot:run

# 1. 管道状态：启动后几十秒内应看到 received == indexedDocs，dropped == 0
curl -X POST http://127.0.0.1:8090/api/logs/stats -H 'Content-Type: application/json' -d '{}'

# 2. 没有真实流量时先造一批数据
curl -X POST http://127.0.0.1:8090/api/logs/generate -H 'Content-Type: application/json' \
  -d '{"count":50,"withException":true,"withTrace":true}'

# 3. 检索（按级别过滤 + 关键字）
curl -X POST http://127.0.0.1:8090/api/logs/search -H 'Content-Type: application/json' \
  -d '{"keyword":"started","levels":["INFO"],"pageNum":1,"pageSize":20}'

# 4. 回填落盘历史日志，多跑几次直到补完
curl -X POST http://127.0.0.1:8090/api/logs/import -H 'Content-Type: application/json' -d '{"maxLines":8000}'

# 5. 页面
open http://127.0.0.1:8090/logs/index.html
```

开关：`dong.logging.enabled` 与 `dong.elasticsearch.enabled` 都为 true 时模块才生效，
否则接口返回 `1004`。关键参数都在 `application.yml` 的 `dong.logging` 下
（`flush-interval-ms`、`flush-max-batch`、`retry-backoff-ms`、`retention-days`、`log-dir`）；
缓冲队列容量是唯一例外——appender 由 logback 创建，早于 Spring 容器，只能在 `logback-spring.xml` 里配。

本次实测（2026-10-27）：启动 20 秒内采集 2438 条、写入 2438 条、丢弃 0；
回填每次调用解析约 175 条并各自归档到发生那天的索引；
`X-Trace-Id` 串联的 traceId 能把一次请求的十几条日志一次捞全；
关闭开关后 `/api/logs/stats` 返回 `1004`。
