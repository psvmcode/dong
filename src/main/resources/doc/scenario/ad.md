# 广告投放模块说明

> 本文是广告投放场景的**全景说明**：业务链路、技术取舍、边界情况、接口清单、数据模型与验证步骤。
> 代码在 `com.dong.ad`，接口前缀 `/api/ad`，实验页面 `/ad/index.html`。

---

## 目录

- [一、模块概览](#一模块概览)
- [二、业务场景](#二业务场景)
- [三、技术场景](#三技术场景)
- [四、边界情况清单](#四边界情况清单)
- [五、接口文档](#五接口文档)
- [六、数据模型](#六数据模型)
- [七、配置与降级](#七配置与降级)
- [八、本地验证](#八本地验证)
- [九、已知简化](#九已知简化)
- [十、稳定性、实战与容灾增强](#十稳定性实战与容灾增强)

---

## 一、模块概览

一句话概括业务：**广告主开户充值 → 建推广计划并审核 → 配单元定向与出价 → 提创意过审 → 媒体请求时召回竞价 → 曝光与点击上报 → 按 CPC/CPM 计费 → 日终按流水对账。**

它和秒杀、抢红包是同一类问题的另一面：那边抢的是有限库存，这边抢的是有限预算。区别在广告链路更长，钱不是一次扣完，而是沿着「请求 → 曝光 → 点击」分几跳慢慢扣，中间任何一跳重复、丢失、迟到，都会变成超投或少收。

| 子包 | 职责 |
|---|---|
| `controller` | 3 个控制器：配置管理、投放链路、报表与对账 |
| `service` + `service/impl` | 配置管理、预算闸门、投放链路、结算对账、并发实验 |
| `mapper` | 7 个 Mapper 接口与 XML，含计划状态 TypeHandler |
| `entity` / `dto` | 7 张表实体、请求响应与消息体（`record`） |
| `enums` | 生命周期、计费类型、事件类型、计费状态、开关状态、闸门路径 |
| `support` | 定向匹配、竞价与金额计算、运行时计数、主题常量 |
| `handler` | 计费消息处理器（本地总线与 RocketMQ 共用） |
| `listener` | `ad-charge-event` 专用 RocketMQ 监听器 |
| `task` | 对账、补投、Redis 副本重建 |

贯穿原则：**计费流水是金额与效果数据的唯一权威源；Redis 只是预算闸门的副本；消息中间件只负责异步化，不负责正确性。**

---

## 二、业务场景

### 2.1 开户、预算与余额

广告主账户有 `balance_cents`（可用余额），推广计划有 `daily_budget_cents`（日预算）与 `total_budget_cents`（总预算）。三道闸门的职责不同：

| 闸门 | 回答的问题 | 落库位置 |
|---|---|---|
| 日预算 | 今天最多花多少 | `ad_daily_stat` 按天一行，条件扣减 |
| 总预算 | 这个计划总共最多花多少 | `ad_campaign.spent_total_cents`，条件扣减 |
| 账户余额 | 账上到底有没有钱 | `ad_advertiser.balance_cents`，条件扣减 |

只卡预算不卡余额，会出现「计划还在投、账户已经没钱结算」；只卡余额不卡预算，会出现「一天把整月预算烧光」。

**今日已花费刻意不放在计划表里。** 跨天重置是个竞态窗口：重置任务晚一秒，新一天的钱就算进旧一天。现在按 `stat_date` 分行的 `ad_daily_stat` 天然完成日切，Redis 预算键也带日期，全系统没有「零点跑一次」的定时任务。

### 2.2 审核与生命周期

计划状态：`DRAFT → PENDING_REVIEW → APPROVED → DELIVERING ⇄ PAUSED → FINISHED`，驳回后可重新提审。迁移表放在 `AdCampaignStatus.next(event)`，落库靠

```sql
update ad_campaign set status = ?, version = version + 1
where id = ? and status = 期望状态 and version = 当前版本
```

两个运营同时点「上线」只有一个成功，另一个得到 1002。上线守卫检查：账户未冻结且余额大于零、至少有一个启用单元、每个启用单元里至少有一条过审创意、投放窗口未过期。少一条，上线后就是「请求有量但一条也投不出去」的空转。

创意单独审核，未过审不参与召回；驳回必须填原因，否则广告主不知道该改什么。

### 2.3 召回、定向与竞价

一次媒体请求依次过七道过滤，每一道刷掉多少候选都记进响应的 `diagnostics`：

1. 黑名单（UA 关键字、IP 前缀）
2. 计划状态与投放日期窗口
3. 投放时段（24 位 0/1 字符串）
4. 广告主状态与余额
5. 日预算 / 总预算余额
6. 频次控制（同一用户同一计划当日曝光数）
7. 单元启用状态、创意过审状态、定向条件

定向四维度（地域 / 人群标签 / 设备 / 关键词）之间是「与」，单维度内是「或」，空列表表示不限。比较统一转小写，避免媒体侧传 `iOS`、配置写 `ios` 就一条都投不出去。

排序用 `score = bid × pctr / 10000`，采用**广义二价（GSP）**：胜出者按刚好压过第二名的价格成交，没有第二名时按保留价成交，且永远不会超过自己的出价。成交价由服务端算好签进令牌，客户端无法改写——否则把自己点成一分钱只需改一个 JSON 字段。

### 2.4 令牌、曝光与点击

`ad_event` 把令牌与事件合在一张表：

| event_type | 含义 |
|---|---|
| 0 REQUEST | 服务端签发的令牌行，带成交价、过期时间、用户与上下文 |
| 1 IMPRESSION | 曝光，一次链路只能有一条 |
| 2 CLICK | 点击，一次链路只能有一条 |

两条唯一键各自负责一类攻击：`uk_event_id` 保证客户端带同一个幂等键重试不重复扣钱；`uk_trace_type` 保证换个 `eventId` 重放同一条曝光/点击也刷不出钱。

**计费类型决定哪一跳真正扣钱**：CPC 只在点击扣，CPM 只在曝光扣；另一类事件照样落库进报表的曝光数、点击数与 CTR，只是金额为 0。把「有效果」和「要扣钱」混为一谈是最常见的计费 bug。

### 2.5 异步计费与日终对账

有效事件写入 `ad_event` 后投递 `ad-charge-event` 消息，`AdChargeEventHandler` 落 `ad_charge_record` 并推进三处金额。日终（以及每 5 分钟的定时任务）以流水为权威源重算 `ad_daily_stat`、回写计划累计花费、重建 Redis 配额副本，并补投超时仍停在 PENDING 的事件。

消息重复、丢失、乱序到达都只造成「暂时不一致」：流水上有 `uk_charge_event`，重投幂等；丢失的事件由 `idx_charge_scan` 扫出来重投；乱序到达按 `stat_date`（事件归属日，不是落库日）归账，跨零点消费不会把昨天的钱算进今天。

---

## 三、技术场景

### 3.1 Redis Lua 预算预扣

键 `lab:ad:budget:{yyyyMMdd}:{campaignId}`，一条脚本完成「键不存在则用库中今日已花费惰性初始化 → 判定配额 → decrby」。因此不需要人工预热，Redis 全量丢失后下一次请求就能自愈。

归还脚本只在键存在时 `incrby`：键过期后回加会从零起底造出一个无 TTL 的「幽灵配额」，让早已结束的日期重新显示可投额度。

**闸门不是裁定者。** 脚本返回「配额不足」时不直接拒绝，而是切回数据库条件扣减，由权威流水做最终裁定——副本可能滞后于库，拿副本当真相会冤枉本来还能投的一笔。所有条件更新都把充足条件写进 `where`，靠影响行数判定：

```sql
update ad_daily_stat
set charge_cents = charge_cents + ?, impressions = impressions + ?, clicks = clicks + ?
where stat_date = ? and campaign_id = ?
  and charge_cents + ? <= budget_cents
```

### 3.2 两条可对照的扣费路径

| 路径 | 做法 | 特点 |
|---|---|---|
| `redis` | Lua 预扣 + 消息异步落库 | 高并发下先挡流，库写入可批量收敛 |
| `db` | 跳过 Redis，直接事务内三条件更新 | 无外部依赖，行锁串行化，链路更短但竞争更集中 |

`/api/ad/report/lab/burst` 可以指定 `mode` 并发打同一条计划的点击链路，回报耗时、平均耗时、被护栏拦下的笔数，并**回读数据库今日已花费**验证有没有超投。只看接口成功数看不出超投，必须拿权威源的数字验证金额守恒。

### 3.3 中间件的降级顺序

| 故障 | 降级行为 | 正确性由谁保证 |
|---|---|---|
| Redis 挂 | 预算闸门切 DB，频次控制跳过 | `ad_daily_stat` 条件更新 |
| MQ 发送失败 | 同步调用计费服务 | `uk_charge_event` + 事务 |
| MQ 消息丢失 | 对账扫 PENDING 重投 | `uk_charge_event` 幂等 |
| RocketMQ 未启用 | 不注册监听器，发送失败自动同步 | 同上 |
| 计费服务瞬时失败 | 事件留 PENDING，接口仍受理 | 对账补偿 |

模块总开关 `dong.ad.enabled=false` 时所有接口返回 1004，不把底层异常暴露成 5000。

### 3.4 为什么给 MQ 单独加监听器

项目已有的 `RocketMqListener` 只订阅 `demo-order-event`，而 `MessageHandler` 是按 topic 精确匹配的。新主题如果没有自己的监听器，现象是「消息发送成功却没人消费，事件永远停在 PENDING」，日志里只有一条不起眼的 warn。所以 `AdChargeRocketMqListener` 单独订阅 `ad-charge-event`，并用 `@ConditionalOnProperty` 跟随 `dong.rocketmq.enabled`，关闭时不会因缺少模板而让应用启动失败。

---

## 四、边界情况清单

| # | 情况 | 处理 |
|---|---|---|
| 1 | 同一 `eventId` 重放 | 返回幂等成功，不重复扣钱 |
| 2 | 同一 `eventId` 换链路/类型复用 | 1006 幂等拒绝，视为客户端 bug 或攻击 |
| 3 | 换 `eventId` 重放同链路同类型事件 | `uk_trace_type` 兜住，落一条 `valid=0` 留痕 |
| 4 | 伪造或不存在的 traceId | 1001 |
| 5 | 令牌过期 | 过滤为 `expired trace`，不计费 |
| 6 | 上报用户与令牌用户不一致 | 过滤为 `user mismatch` |
| 7 | 点击没有前置曝光 | 过滤为 `click without impression` |
| 8 | 点击距曝光过短（机器行为） | 过滤为 `too fast click` |
| 9 | 点击超出归因窗口 | 过滤为 `click out of window` |
| 10 | UA / IP 命中黑名单 | 请求阶段直接空填充，事件阶段过滤 |
| 11 | 事件到达时计划已暂停/结束、创意被驳回、单元被暂停 | 不计费，事件留痕 |
| 12 | 同一用户当日看太多次 | 召回阶段剔除该计划，曝光时递增计数 |
| 13 | 日预算 / 总预算 / 账户余额任一不足 | 写 `status=3` 拒绝流水，反向补偿已成功的扣减，归还 Redis 预扣 |
| 14 | 并发下条件更新才失败（预检通过） | 反向补偿 `spent_total` 与余额，再写拒绝流水 |
| 15 | 调大日预算 | 同步刷新当天预算快照并重建 Redis 副本，否则新预算当天花不出去 |
| 16 | 广告位没有候选 | 返回 `matched=false` 与原因，不抛异常，避免媒体侧重试风暴 |
| 17 | 定向 JSON 写脏 | 该候选跳过并记 warn，不让一次请求整体失败 |
| 18 | 投放时段配置写脏 | 放行并记 warn，避免一条配置让全站零填充 |
| 19 | 消息畸形 / 缺字段 | 丢弃并返回 ACK，无限重投只会堵死队列 |
| 20 | 消息重复投递 | `uk_charge_event` 幂等，返回 ACK 不重投 |
| 21 | 计费服务瞬时不可用 | 事件留 PENDING，对账任务补投 |
| 22 | 跨零点消费 | `stat_date` 取事件归属日，不串账 |
| 23 | 对账流水超过安全容量 | 抛 1007 拒绝截断，截断会得出错误金额结论 |
| 24 | 对账重跑同一天 | 以流水覆盖汇总，结果幂等 |
| 25 | 两人同时改预算 / 上线 / 审核 | 乐观锁与条件更新，失败方返回 1002 |
| 26 | 金额精度 | 全程 `long` 存分，无浮点，日终对账不会出现一分钱差额 |
| 27 | 参数越界（负数、超大出价、超大批量、超长日期跨度） | `Constants.MAX_*` 封顶，拒绝越界 |
| 28 | 实验参数放大 | 线程数、循环数与总任务量三重封顶 |
| 29 | Redis 归还时键已过期 | 只在键存在时回加，不造幽灵配额 |
| 30 | 查询无结果 | 返回空列表 / 空分页，不报错 |

---

## 五、接口文档

全站接口统一为 POST，参数走 JSON body。通用动作请求 `AdActionRequest` 按字段取值：`id` 必填，`event` 状态迁移、`remark` 审核备注、`decision` 创意审核、`amountCents` 充值、`enabled` 单元开关。

### 配置管理 `/api/ad/manage`

| 路径 | 说明 |
|---|---|
| `/advertiser/create` | 开户并初始充值 |
| `/advertiser/recharge` | 充值，返回新余额 |
| `/campaign/create` | 建计划（日预算、总预算、起止日期、24 位投放时段） |
| `/campaign/update` | 调整预算、结束日期、时段 |
| `/campaign/detail` | 计划详情，含今日已花费 |
| `/campaign/transition` | 统一状态迁移：SUBMIT / APPROVE / REJECT / START / PAUSE / RESUME / FINISH |
| `/unit/create` | 建单元（定向、CPC/CPM、出价、保留价、pctr） |
| `/unit/update` | 调价、调定向、调 pctr |
| `/unit/toggle` | 启用 / 暂停 |
| `/creative/create` | 建创意，待审核 |
| `/creative/review` | 通过 / 驳回，驳回必须给原因 |
| `/query` | `target=advertiser\|campaign\|unit\|creative` 的分页查询 |
| `/runtime` | 运行时状态、消息通道、消费计数 |

### 投放链路 `/api/ad/delivery`

| 路径 | 说明 |
|---|---|
| `/request` | 召回竞价，返回胜出创意、成交价、traceId、候选明细与过滤诊断 |
| `/event/impression` | 曝光上报，CPM 在此计费 |
| `/event/click` | 点击上报，CPC 在此计费 |
| `/event/query` | 分页查链路事件与令牌，看原始 `valid` / `chargeStatus` |
| `/runtime` | 链路计数与降级次数 |

### 报表与实验 `/api/ad/report`

| 路径 | 说明 |
|---|---|
| `/daily` | 日报表：曝光、点击、花费、CTR、平均点击成本、预算使用率、超投标记 |
| `/reconcile` | 手动对账：重算汇总、回写累计花费、重建 Redis 副本、补投漏单 |
| `/lab/burst` | 并发对照实验，`mode=redis\|db` |

---

## 六、数据模型

DDL 唯一权威源是 `../../db/schema.sql`，共 7 张表：

| 表 | 关键设计 |
|---|---|
| `ad_advertiser` | 余额、状态、乐观锁 |
| `ad_campaign` | 双层预算、累计花费、投放日期与时段、生命周期、乐观锁；**不存今日花费** |
| `ad_unit` | 定向 JSON、计费类型、出价、保留价、pctr、启用状态 |
| `ad_creative` | 素材、落地页、审核状态、驳回原因 |
| `ad_event` | 令牌与事件合一；`uk_trace_type` + `uk_event_id` 双唯一键；`idx_charge_scan` 支撑补投 |
| `ad_charge_record` | 金额与效果数据的唯一权威源；`uk_charge_event` 幂等；`stat_date` 归账 |
| `ad_daily_stat` | 按天分行，预算快照写进条件扣减的 where；跨日不重置任何字段 |

改完 `schema.sql` 必须执行 `../../deploy/gen-initdb.sh` 重新生成 `initdb/`，这两个目录是生成物，禁止手改。已有容器不会重跑初始化脚本，新增表要单独在远程库执行。

---

## 七、配置与降级

配置在 `application.yml` 的 `dong.ad` 段：

| 配置 | 默认 | 说明 |
|---|---|---|
| `enabled` | true | 模块总开关，关闭返回 1004 |
| `redis-budget-enabled` | true | 关闭或 Redis 不可用时降级纯 DB |
| `mq-charge-enabled` | true | 关闭或发送失败时同步计费 |
| `frequency-cap-enabled` / `frequency-cap-per-day` | true / 3 | 单用户单计划每日曝光上限 |
| `trace-ttl-minutes` | 30 | 令牌有效期 |
| `min-click-delay-ms` / `click-window-seconds` | 200 / 1800 | 无效点击判定窗口 |
| `blocked-ua-keywords` / `blocked-ip-prefixes` | bot,spider,crawler / 空 | 黑名单 |
| `cpm-divisor` | 1000 | CPM 千次价格折算到单次 |
| `reconcile-enabled` / `reconcile-interval-ms` / `reconcile-lookback-days` | true / 300000 / 2 | 对账任务 |
| `charge-pending-minutes` / `charge-resend-limit` | 5 / 100 | 漏单补投 |

`dong.mq.active=local` 时本地总线同步派发到 handler；`rocketmq` 时走专用监听器；`kafka` 没有订阅该主题的监听器，发送失败会自动同步降级，功能不受影响。

---

## 八、本地验证

> 本地不装任何中间件，全连云服务器。**新增的 7 张表需要单独在远程库执行 DDL**（现有容器不会重跑初始化脚本），否则接口会报表不存在。

页面 `/ad/index.html` 提供一键搭建与全链路操作，也可以按下面的顺序用接口验证：

1. `manage/advertiser/create` → `manage/campaign/create` → `manage/unit/create`（建议 `bidType=1` CPC）→ `manage/creative/create`
2. `manage/creative/review`（`decision=approve`）→ `manage/campaign/transition`（`event=SUBMIT`）→ `APPROVE` → `START`
3. `delivery/request` 拿 `traceId`，用同一 `userId` 依次调 `event/impression`、`event/click`
4. `report/daily` 看曝光、点击、花费、CTR；CPC 计划应看到曝光不扣钱、点击扣钱
5. 重复调用第 3 步的点击（同一个 `eventId`），确认返回 `duplicate=true` 且金额不变
6. `report/reconcile` 看差异明细为 0；关掉 `redis-budget-enabled` 再跑一遍链路，确认功能正常、`chargeMode=db`
7. `report/lab/burst` 分别用 `mode=redis` 与 `mode=db` 跑同一批任务，对比耗时与 `dbChargeCents <= budgetCents`

验证异常分支：把 `traceId` 改错、把 `userId` 改掉、UA 填 `spider`、重复上报换 `eventId`、把日预算调到 1 分，逐一观察 `reason` 与日报表里的 `rejectedCents`。

---

## 九、已知简化

| 简化 | 真实系统做法 | 影响 |
|---|---|---|
| CPM 单次摊销成一条流水 | 累计曝光满千次才结算一次 | 单笔金额偏小，但总额以流水为准守恒，幂等与对账更简单 |
| pctr 由配置写死 | 在线 CTR 预估模型实时打分 | 竞价排序仍是同一套公式，只是分数不再变化 |
| 频次按曝光计数、只按日 | 跨计划、跨设备、滑动窗口去重 | 够演示护栏，不覆盖跨设备身份归一 |
| 无效流量只做规则判断 | 设备指纹、IP 库、行为模型、第三方反作弊 | 覆盖重放、过期、乱序、UA/IP 等确定性作弊 |
| 二价按「压过第二名 1 分」 | 保留价、质量分、动态底价联合定价 | 成交价逻辑一致，参数更少便于对照 |
| 报表实时查汇总表 | 数仓离线 + 实时流双路 | 异步计费期间报表有秒级延迟，由对账收敛 |
| 对账单计划流水上限 1000 | 单片超限跳过该计划并在明细里说明 | 其他计划照常收敛，不会被一个大客户拖住全量对账 |

---

## 十、稳定性、实战与容灾增强

### 10.1 稳定性与代码防御

| 机制 | 实现 | 为什么需要 |
|---|---|---|
| 广告位级限流 | Redis 固定窗口脚本，按 placement + 秒计数；Redis 挂了降级到本地 Caffeine 秒级计数 | 全局 IP 限流挡不住「单个热点广告位把召回打穿」 |
| 双依赖熔断 | Redis 与 MQ 各自 CLOSED / OPEN / HALF_OPEN 三态，连续失败达阈值后直接快速失败，冷却期只放一个探针 | 没有熔断时每个请求都要白等 5 秒客户端超时，线程池被注定失败的请求占满 |
| 请求时间预算 | 广告请求带 200ms deadline，逐计划过滤时检查，超时就跳过剩余计划并在诊断里标记 | 慢依赖不能让一次请求无限延长 |
| 实验并发闸门 | 全局 Semaphore 限制同时运行的实验数，共享有界线程池替代每次新建池，队列满直接拒绝 | 多个实验并发会把应用线程吃光 |
| 候选返回封顶 | 明细最多 10 条，双重上限 `min(配置, Constants.MAX_CANDIDATES)` | 一次普通请求不该拖回整个候选集 |
| Lua 金额防御 | 预扣脚本内校验 `amount > 0`，非法值返回独立错误码 | 不依赖调用方永远正确 |
| N+1 治理 | 计划列表的今日消耗改成一次批量查询；账户汇总对账用 SQL `sum` | 单页 200 条不再打 200 次数据库 |
| 脏数据隔离 | 定时段串、脏投放时段放行并告警；脏定向只跳过该候选；无效计数按 0 处理 | 一条脏配置不能让整个接口失败 |

### 10.2 实战业务增强

**预算平滑投放（pacing）**：按当天已过分钟数算出「此刻理论上应该累计花到多少」，花得比节奏快太多就先刹车。这是软闸门——数据库日预算仍是硬闸门，所以副本或时钟出偏差时最多少投，不会多扣一分钱。响应诊断里能看到 `pacingFiltered`，页面可以解释「为什么还有预算却没投」。

**账户日限额**：开户时默认 0（不限），可显式调大。它是第四道护栏，位于总预算、账户余额、日预算之外，防止多个计划一起把同一个账户花穿。调整时同步刷新当天快照，否则条件扣减还按旧限额挡着。

**召回候选缓存**：全局快照 + 30 秒兜底 TTL + 写操作主动失效。单元调价、定向变更、创意审核、计划状态迁移都会立即清缓存；TTL 只是防止某条失效路径被遗漏后永久脏读。空结果不缓存——新广告上线要立刻能被召回。

**操作审计**：充值、预算调整、状态迁移、单元调价/开关、创意审核、对账修正都写 `ad_audit_log`，记录改前改后快照与原因。审计是旁路：写失败只记错误日志，绝不把主流程拖垮。

### 10.3 容灾与数据治理

**对账分片加锁**：不再一次读全天流水。先分页取有流水的计划 id，再按计划一片一片聚合；某片流水超过安全容量只跳过这片并在明细里说明，其他片照常收敛。整次对账持 Redis 分布式锁，多实例部署时只有一个实例重算，避免两份汇总互相覆盖。账户日汇总用 SQL `sum` 按广告主重算，不需要把几十万条流水读进内存。

**PENDING 退避与死信**：补投失败不再固定 5 分钟重试，而是按 `基数 × 2^n` 逐步放大，上限 24 小时；超过最大次数标记为 DEAD（死信），不再自动重投，等人工介入。`next_resend_time` 写进事件行，未到点的事件不会被扫到。

**在线数据归档**：事件与流水带幂等唯一键、天然只增不减，定时把过期流水搬到 `ad_charge_archive`，**先写归档表、再删在线表**，且只删除确认已归档的 id。归档失败不丢账。仍待计费的事件不清理，避免清掉补偿线索。

**健康检查**：`/api/ad/report/health` 分别探测数据库、Redis、MQ、熔断状态与 PENDING / DEAD 积压。每个探针独立捕获，一个失败不影响整份报告；Redis 熔断打开时直接跳过探活，不为已知故障再等满超时。

**定时任务防重复**：全站维护类定时任务统一用 `@DistributedTaskLock` 注解，由切面集中拿锁、判断是否持有、释放；拿不到锁就跳过本轮，锁服务不可用时也不退化成无锁执行。注册心跳与本地日志缓冲刷写刻意不加锁——它们必须每个实例各跑各的。

### 10.4 新增接口

| 路径 | 说明 |
|---|---|
| `/manage/advertiser/update-limit` | 调整账户日限额，0 表示不限 |
| `/report/health` | 依赖探针与积压检查 |
| `/report/audit/query` | 分页查询配置变更轨迹 |
| `/report/cleanup` | 手动触发归档与过期事件清理 |

### 10.5 新增表与字段

| 表 | 用途 |
|---|---|
| `ad_advertiser_daily` | 账户日限额与当日已花费，按天分行，SQL 条件扣减 |
| `ad_audit_log` | 配置变更审计，含改前改后 JSON 快照 |
| `ad_charge_archive` | 过期流水归档，幂等键仍唯一 |
| `ad_event` 新增 | `resend_count`、`next_resend_time`，支撑退避与死信 |
| `ad_advertiser` 新增 | `daily_limit_cents`，账户日限额 |

### 10.6 新增配置

```yaml
dong:
  ad:
    request-rate-limit-enabled: true
    request-rate-limit-per-second: 100
    breaker-failure-threshold: 5
    breaker-open-duration-ms: 10000
    request-timeout-ms: 300
    lab-max-concurrent-experiments: 2
    max-candidates: 10
    pacing-enabled: true
    pacing-factor: 1.0
    pacing-tolerance: 0.2
    candidate-cache-enabled: true
    candidate-cache-ttl-seconds: 30
    candidate-cache-max-size: 5000
    audit-enabled: true
    reconcile-shard-size: 200
    reconcile-lock-lease-ms: 60000
    retention-days: 7
    retention-batch-size: 200
    retention-task-enabled: true
    pending-max-resend: 5
    pending-resend-backoff-ms: 300000
    health-enabled: true
```

### 10.7 验证补充

1. `/delivery/runtime` 看限流、熔断、缓存计数；`/report/health` 看依赖与积压
2. 调大限流开关下的 `placement` 请求频率，观察响应 `reason=rate limited`
3. 把 MQ 开关关掉或停掉 RocketMQ，连续发请求，观察熔断器 `open=true` 时不再等待超时
4. 把日预算调大、把 `pacing-factor` 调小，观察 `pacingFiltered` 与报表花费节奏
5. 调一次日预算，再查 `/report/audit/query`，确认改前改后都在
6. 手动 `/report/cleanup`，确认流水搬去归档表且在线表减少；重跑一次确认幂等
7. 对账时人为插入超大流水让单片超限，确认只跳过该片，其他计划仍被修正

### 10.8 新增边界情况

| # | 情况 | 处理 |
|---|---|---|
| 31 | 单广告位突发洪水 | 独立限流，超限空填充并记诊断 |
| 32 | Redis 或 MQ 连续失败 | 熔断快速失败，冷却期只放一个探针 |
| 33 | 召回慢依赖 | 200ms 时间预算，超时跳过剩余计划 |
| 34 | 多个并发实验 | 全局信号量限流，共享有界线程池，队列满直接拒绝 |
| 35 | 花得比时间节奏快 | pacing 软闸门先刹车，DB 日预算仍硬挡 |
| 36 | 多计划把账户花穿 | 账户日限额第四道条件更新，失败逆序补偿 |
| 37 | 暂停后缓存仍返回旧候选 | 写操作主动失效 + 30 秒兜底 TTL |
| 38 | 审计表写入失败 | 记错误日志，不影响预算调整主流程 |
| 39 | 大促当天流水超大 | 对账按计划分片，单片超限跳过并说明 |
| 40 | 多实例同时跑对账 | 分布式锁保证单实例执行 |
| 41 | 补投一直失败 | 指数退避，超过上限转死信不再重投 |
| 42 | 归档时写归档表失败 | 不删在线流水，下轮重试，账不会丢 |
| 43 | 健康检查时 Redis 已熔断 | 跳过探活，报告标记 breaker open |
| 44 | 候选明细传超大值 | 双重上限封顶，最多 10 条 |

