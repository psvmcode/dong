# 跨境支付模块说明

> 本文是跨境支付的**全景说明**，覆盖业务场景、技术场景与接口清单三部分。
> 若想深入理解某一处的设计取舍，另见 `../crossborder-payment.md`（业务深度）与
> `../crossborder-interview-prep.md`（面试向讲解，未纳入版本库）。

---

## 目录

- [一、模块概览](#一模組概览)
- [二、业务场景](#二业务场景)
- [三、技术场景](#三技术场景)
- [四、接口文档](#四接口文档)
- [五、数据模型](#五数据模型)
- [六、运维与监控](#六运维与监控)

---

## 一、模块概览

代码位于 `com.dong.crossborder`，对外前缀统一为 `/api/crossborder`。

一句话概括业务：**把付款方的一种货币，按锁定的汇率换成另一种货币，合规审查后通过清算渠道送到境外收款方，并在事后与渠道逐笔对账。**

境内转账只有「从 A 挪到 B」一件事，跨境要同时办好四件事：

| 维度 | 境内 | 跨境 |
|---|---|---|
| 币种 | 同一种，直接相加 | 两端不同，必须换汇，有汇率风险 |
| 时间 | 实时或准实时 | SWIFT 可达两天，本地清算也要分钟级 |
| 合规 | 反欺诈即可 | 制裁名单、KYC 等级、反洗钱申报、限额 |
| 通道 | 央行清算系统，唯一 | SWIFT / CIPS / 本地清算，成本与时效差异大 |

模块划分：

| 子包 | 职责 |
|---|---|
| `controller` | 6 个控制器，全部接口入口 |
| `service` + `service/impl` | 业务逻辑 |
| `mapper` | MyBatis 数据访问（XML 在 resources 下） |
| `entity` | 数据库实体 |
| `dto` | 请求与响应对象 |
| `enums` | 状态与类型枚举 |
| `handler` | 清算消息消费者 |
| `task` | 定时任务（补偿、对账、卡单检测） |

---

## 二、业务场景

### 2.1 开户与账户管理

开立跨境账户时设定币种、KYC 等级、单笔与日累计限额。开户币种必须是牌价表里已启用的币种，否则会开出永远无法询价的账户。

账户余额分为**可用余额**与**冻结余额**：钱还在账上但被冻结时不能动，这是司法冻结与反洗钱调查的基本要求。

冻结与解冻每次都会写事件表，**只增不改**——状态字段回答「现在能不能用」，事件表回答「怎么变成这样的」，监管检查时要的是后者。

### 2.2 汇率牌价与锁汇

牌价以**美元为桥**存储：表里存「一美元兑换多少该币种」，任意两币种之间的汇率由各自对美元的牌价相除得出。

```
CNY/USD = USD 的牌价 ÷ CNY 的牌价 = 1.0 ÷ 7.15 ≈ 0.13986
```

这样只需维护 N 条记录就能支持 N×(N−1) 个货币对。

询价返回**买卖双价**：

| 价 | 含义 | 客户用哪个 |
|---|---|---|
| bid 买入价 | 银行向你买外汇的价格 | 你卖外汇给银行 |
| **ask 卖出价** | 银行卖外汇给你的价格 | **你换汇买入，按这个成交** |

两者之差是**点差**，即银行收益。客户按 ask 成交，到手金额比按中间价算的少。

**锁汇**把报价绑定到此笔汇款，此后市场涨跌与双方无关。这是规避汇率风险的核心手段，代价是平台承担了**敞口**。

### 2.3 汇款主链路

```
开户 → 询价 → 合规筛查 → 锁汇 → 扣款记账 → 发清算消息
     → 归批 → 清算中 → 渠道确认 → 收款方入账 → 对账
```

关键约束：

- 两端币种必须不同，同币种直接拒绝
- 扣款、记账、状态推进**同一事务**
- 清算消息在事务**提交之后**才发，避免「回滚了但下游已放款」

### 2.4 合规筛查

四道检查依次执行，取最严重的结论：

| 顺序 | 检查 | 触发 | 处置 |
|---|---|---|---|
| 1 | 制裁名单 | 户主在黑名单 | **直接拒绝** |
| 2 | KYC 等级 | 金额超过等级额度 | **直接拒绝** |
| 3 | 反洗钱 | 金额超过阈值 | **挂人工审核**（大额本身合法） |
| 4 | 限额 | 超单笔或日累计 | **直接拒绝** |

两个关键设计：

- **制裁名单查不到时按命中处理**：误拒只是客户不悦，漏放制裁对象会让机构被重罚，代价差几个数量级。
- **日限额累加与判断在同一 Lua 脚本内完成**：拆成「先读再写」两步会产生竞态窗口，并发下限额形同虚设。

### 2.5 人工审核

大额触发人工审核后，汇款单停在待审核状态，**资金尚未扣减**。

- **放行**：按放行时刻重新锁汇、扣款、发送清算消息。放行前会重新校验账户状态——挂起与放行之间可能隔数小时，账户可能已被冻结。中途失败退回待审核而不是判死。
- **驳回**：直接进终态，并释放占用的日累计额度。

审核人姓名必填：合规决策必须能追溯到具体的人。

### 2.6 清算与批次

真实跨境不是来一笔发一笔，而是**攒一批按点发**：成本低、好对账。

清算分两步推进，中间状态「清算中」代表资金已交付渠道但尚未确认到账——也就是**在途资金**。

| 状态 | 含义 | 钱在哪 |
|---|---|---|
| 已扣款 | 已从付款方扣掉 | 平台 |
| **清算中** | 已交付渠道，等确认 | **渠道（在途）** |
| 已结算 | 渠道确认到账 | 收款方 |

### 2.7 对账

拿本地记录与渠道回单逐笔比对：

| 差异 | 含义 | 危险度 |
|---|---|---|
| 金额不符 | 两边金额不一致，多为中间行扣费或精度差 | 中 |
| 短款（本地有渠道无） | 渠道漏报 | 中 |
| **长款（渠道有本地无）** | 渠道多放或本地漏记 | **最高** |

长款最危险，因为意味着存在一笔你不知道的钱。处理原则是**挂账 → 查证 → 调账**，绝不悄悄抹平。

### 2.8 风控

| 能力 | 作用 |
|---|---|
| 渠道路由 | 按「手续费 + 到账时长×时效权重」打分择优，加急时时效权重放大五倍 |
| 拆分交易监控 | 抓「累计达申报线、每笔都在线下、笔数达门槛」的可疑模式 |
| 汇率敞口 | 统计已锁汇未清算的净头寸与浮动盈亏，超线提示平盘 |

### 2.9 退汇

资金已到收款方之后发现问题（账号错、被拒收、监管要求），需要**原路退回**。

与「退款」必须分清：

| | 退款 | 退汇 |
|---|---|---|
| 时机 | 钱还没汇出去 | 钱已到收款方 |
| 损失 | 无 | 通常已有汇兑损失 |
| 手续费 | — | **不退**（通道成本已发生） |

退汇先扣回收款方，再退回付款方本金。**若收款方余额不足，不允许凭空造钱退给付款方**，直接报错转人工追讨。

### 2.10 流转日志

每一次状态推进都会记一条日志，**成功与被拒绝都记**。

状态字段只能回答「现在是什么状态」，流转日志回答「它怎么变成这样的」。客户投诉、监管检查、故障复盘，靠的都是后者。审核类操作会把审核人写进日志。

### 2.11 渠道管理

渠道的时效、单笔上限、费率都可在线调整。**停用渠道等于熔断**——故障期间把它从路由候选里摘掉，流量自动切到其他渠道。

---

## 三、技术场景

### 3.1 幂等：三层防护

| 层 | 做法 |
|---|---|
| 一 | 先查幂等键，命中直接返回原单 |
| 二 | 分布式锁内**双查** |
| 三 | 数据库唯一索引兜底 |

**第三层的处理是重点**：冲突时**不能抛异常，必须返回已存在的那笔单子**。

否则客户端收到冲突错误会以为汇款失败，换新单号重试——结果真的汇了两次。

> 幂等的定义是「调用 N 次与 1 次结果一致」，这包含**返回值一致**。抛异常会让第二次返回一个和第一次不同的结果，本身就违反幂等。

### 3.2 事务边界

- 扣款、记账、状态推进在同一事务
- 消息在 `afterCommit` 之后发送
- 入账逻辑放在**独立 bean**（`CrossBorderLedgerService`）：同类 `this` 调用会绕过 Spring 代理导致事务静默失效，曾因此出现重复入账

### 3.3 并发控制

状态推进用**期望状态 + 版本号**双条件：

```sql
update cross_border_remittance
set status = #{target}, version = version + 1
where remittance_no = #{no}
  and status = #{expected}
  and version = #{version}
```

两个条件分工：`status` 防止跳步，`version` 防止覆盖他人写入。

扣款则靠**数据库条件更新**防超扣：

```sql
where id = #{id} and balance - frozen_balance >= #{amount}
```

### 3.4 防重复入账

最后一道防线是流水的唯一索引：同一汇款单、同一账户、同一方向只允许一条。

即使应用层有 bug，数据库也会直接报冲突让整个事务回滚。**把最忌讳的事故交给数据库在最底层挡住。**

### 3.5 补偿与兜底

| 机制 | 作用 |
|---|---|
| 消息补偿 | 每 30 秒重发未推进的清算消息，创建 2 分钟内不补（静默期防消息风暴） |
| **重试上限** | 20 次后停止自动重试并报错，避免坏单被无限重发 |
| **人工重试** | 人工处理完调用重试接口重置计数，重新进入补偿 |
| 卡单检测 | 每 5 分钟扫描停留超 30 分钟的「已扣款/清算中」并告警 |
| 每日对账 | 每天凌晨自动对账，有差异就报错 |

> 重试上限**必须**配人工重试入口。只有限制没有出口，会让可恢复的失败（如收款方冻结后解冻）永久卡死——比无限重试更糟。

### 3.6 数据落库与缓存

**业务数据一律落库**，不存内存：

| 数据 | 存储 |
|---|---|
| 汇率牌价 | `cross_border_fx_rate` 表 |
| 渠道时效/限额/费率 | `cross_border_channel_config` 表 |
| 日累计限额占用 | Redis（带 48 小时过期，按自然日切分） |
| 拆分交易监控 | Redis（同上） |
| 账务与单据 | MySQL |

内存中的 `LongAdder` 只做**运行时观测指标**，不作为账务依据，重启归零也不影响正确性。

Redis 缓存仅用于**中间价**（30 秒），调整牌价时会主动失效。

### 3.7 状态机

```
CREATED → QUOTE_LOCKED → FUNDS_DEBITED → SETTLING → SETTLED
   │            │              │             │          │
   │            │              │             │          └→ RETURNING → RETURNED（退汇）
   │            │              │             └────────────→ FAILED → REFUNDED
   ├──→ COMPLIANCE_REJECTED（终态）
   └──→ PENDING_REVIEW →（放行）QUOTE_LOCKED ／（驳回）COMPLIANCE_REJECTED
```

**失败一律走退款，绝不回退状态**——回退会产生说不清去向的在途资金。唯一例外是审核放行中途失败，因为此时资金尚未扣减。

### 3.8 输入防御

所有入参都有边界约束（详见 `../crossborder-payment.md` 与代码注释）：

- 金额：`@DecimalMin` + `@Digits(integer=16, fraction=2)`，小数位与 `decimal(18,2)` 对齐，避免静默四舍五入
- 字符串：`@Size` 与数据库字段长度一致，超长会变成 500 而非业务拒绝
- 开户金额不允许为负：负余额等于凭空造负债，负限额让风控失效
- 分页：`pageNum` 有上限，防止 `(pageNum-1)×pageSize` 溢出成负数

---

## 四、接口文档

统一前缀 `/api/crossborder`。响应统一包装为 `Result<T>`：

```json
{"code":0,"message":"success","data":{...},"timestamp":1788700000000}
```

常用错误码：`1000` 参数错误、`1001` 数据不存在、`1002` 业务冲突、`1004` 中间件未启用、`5000` 内部错误。

### 4.1 账户

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/accounts` | 开户，币种必须是牌价表里已启用的 |
| GET | `/accounts/{accountNo}` | 按账号查账户 |
| GET | `/accounts` | 查询全部账户 |
| GET | `/accounts/{accountNo}/diff` | 账实校验，用流水倒推余额比对（入参 initial 必须为精确小数） |
| POST | `/accounts/{accountNo}/freeze` | 冻结，参数 reason、operator |
| POST | `/accounts/{accountNo}/unfreeze` | 解冻 |
| GET | `/accounts/{accountNo}/events` | 冻结解冻历史 |
| POST | `/sanction` | 加入制裁名单，参数 ownerName |
| DELETE | `/sanction` | 移出名单 |
| GET | `/sanction/count` | 名单大小 |

### 4.2 汇率

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/fx/quote` | 询价，参数 sourceCurrency、targetCurrency、validSeconds(1~86400) |
| GET | `/fx/{quoteNo}` | 查报价详情 |
| GET | `/fx/available` | 查某货币对的可用报价 |
| GET | `/fx/rate` | 查当前中间价（走 30 秒缓存） |
| GET | `/fx/rates` | 查全部牌价 |
| POST | `/fx/rate` | 调整牌价，同时失效中间价缓存 |
| POST | `/fx/expire` | 手工清理过期报价（定时任务也会做） |

### 4.3 汇款

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/remittance` | 发起汇款，body 见下 |
| GET | `/remittance/{remittanceNo}` | 按单号查 |
| GET | `/remittance/by-idempotent/{key}` | 按幂等键查，超时后确认是否已受理 |
| GET | `/remittance` | 分页查询，可按 status 过滤 |
| GET | `/remittance/{no}/compliance` | 合规检查明细（四道检查各一行） |
| GET | `/remittance/{no}/events` | 状态流转历史 |
| GET | `/remittance/pending-review` | 待人工审核列表 |
| POST | `/remittance/{no}/review/approve` | 审核放行，body 含 reviewer、note |
| POST | `/remittance/{no}/review/reject` | 审核驳回 |
| POST | `/remittance/{no}/return` | **退汇**，参数 reason、operator |
| POST | `/remittance/{no}/retry` | 人工介入后重置重试计数并重新投递 |
| GET | `/remittance/by-batch/{batchNo}` | 按批次查 |
| GET | `/remittance/runtime` | 运行时统计 |

发起汇款请求体：

```json
{
  "idempotentKey": "order-20260908-001",
  "payerAccountNo": "CB...",
  "payeeAccountNo": "CB...",
  "sourceAmount": 1000.00,
  "quoteNo": "FQ...",
  "channel": "SWIFT",
  "urgent": false
}
```

`quoteNo` 与 `channel` 可省略：前者由服务端临时询价，后者由渠道路由自动选择。

### 4.4 清算

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/settlement/channels` | 查渠道配置（时效、限额、费率、启停） |
| POST | `/settlement/channel/{channel}` | 调整渠道参数 |
| POST | `/settlement/channel/{channel}/toggle` | 启停渠道（熔断开关） |
| POST | `/settlement/batch` | 建批次，参数 channel、currency、cutoffMinutes |
| GET | `/settlement/batch/{batchNo}` | 批次详情 |
| GET | `/settlement/batch` | 全部批次 |
| POST | `/settlement/batch/{no}/collect` | 把已扣款单子归集进批次 |
| POST | `/settlement/batch/{no}/settle` | 清算并入账 |
| POST | `/settlement/close-overdue` | 关闭到期批次 |
| GET | `/settlement/status` | 批次状态分布 |

### 4.5 对账

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/recon/{batchNo}` | 执行对账，参数 errorRate(0~1) 模拟渠道差错 |
| GET | `/recon/{batchNo}/channel-statement` | 模拟渠道回单 |
| GET | `/recon/{batchNo}/report` | 对账报告 |
| POST | `/recon/diff/{id}` | 处理单条差异 |
| POST | `/recon/{batchNo}/handle-all` | 批量处理差异 |
| GET | `/recon/overview` | 差异总览 |

### 4.6 风控

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/risk/route` | 渠道路由试算，参数 amount、urgent |
| GET | `/risk/aml/profile` | 付款人当日画像，参数 payerAccountId |
| GET | `/risk/aml/flagged` | 命中拆分嫌疑的账户 |
| DELETE | `/risk/aml` | 清空监控数据 |
| POST | `/risk/aml/reset-daily` | 重置某账户日限额占用 |
| GET | `/risk/fx-exposure` | 汇率敞口 |

---

## 五、数据模型

共 11 张表（含本次新增 3 张）：

| 表 | 用途 |
|---|---|
| `cross_border_account` | 账户主体，余额与冻结余额分离 |
| `cross_border_account_event` | 冻结解冻留痕，只增不改 |
| `cross_border_account_ledger` | 资金流水，唯一索引防重复入账 |
| `cross_border_fx_quote` | 汇率报价，带有效期 |
| `cross_border_fx_rate` | **汇率牌价**（新增，取代代码常量） |
| `cross_border_channel_config` | **渠道配置**（新增，取代代码常量） |
| `cross_border_remittance` | 汇款单 |
| `cross_border_remittance_event` | **流转日志**（新增） |
| `cross_border_compliance_record` | 合规检查记录，每道检查一行 |
| `cross_border_settlement_batch` | 清算批次 |
| `cross_border_recon_diff` | 对账差异 |

完整 DDL 见 `../../db/schema.sql`。

> 改完 DDL 必须执行 `../../deploy/gen-initdb.sh` 重新生成 `../../deploy/initdb/`，
> 两个 initdb 是容器首次启动时用的「库内脚本」，无建库语句，**不要手工编辑**。

---

## 六、运维与监控

### 6.1 定时任务

| 任务 | 频率 | 作用 |
|---|---|---|
| 清理过期报价 | 1 分钟 | 过期报价不再可锁定 |
| 关闭到期批次 | 1 分钟 | 推进清算窗口 |
| 消息补偿 | 30 秒 | 重发未推进的清算消息 |
| 每日对账 | 每天 3:00 | 自动对账，有差异报错 |
| 卡单检测 | 5 分钟 | 发现停留过久的单子 |

### 6.2 需要盯的指标

- `GET /remittance/runtime` 中各状态数量：**已扣款与清算中的数量应长期为 0**，不为 0 说明有卡单
- `settlingEntered`：累计进入清算中的笔数（瞬时状态数看不到，看累计）
- 日志中 `stuck remittance detected` / `compensation abandoned` / `reconciliation diff found`：出现即为异常

### 6.3 日志

`logback-spring.xml` 配置，输出到 `logs/`：

| 文件 | 内容 |
|---|---|
| `dong.log` | 全量，所有级别 |
| `dong-debug.log` | 仅 DEBUG（含 MyBatis SQL） |
| `dong-warn.log` | 仅 WARN |
| `dong-error.log` | 仅 ERROR，排查时第一个看它 |

### 6.4 中间件依赖

| 组件 | 用途 | 不可用时 |
|---|---|---|
| MySQL | 账务与单据 | 不可用则整体不可用 |
| Redis | 日限额、AML 监控、中间价缓存 | 制裁检查按命中处理（fail-safe），其余拒绝 |
| RocketMQ | 清算消息 | 发送失败由补偿任务重推 |
| MariaDB | 第二数据源（replica 演示） | 可用 `--dong.mariadb.enabled=false` 关闭 |
| Elasticsearch | 商品搜索 | 可用 `--dong.elasticsearch.enabled=false` 关闭 |

---

## 附：本地验证

```bash
# 启动（按需关闭不可用组件）
mvn spring-boot:run -Dspring-boot.run.arguments="--dong.elasticsearch.enabled=false --dong.mariadb.enabled=false"

# 主界面
open http://127.0.0.1:8090/doc.html
```

完整链路的 curl 示例见 `../crossborder-payment.md` 第九节。
