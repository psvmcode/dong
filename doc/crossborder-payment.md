# 跨境支付业务专项学习

> 配套代码：`src/main/java/com/dong/lab/crossborder/`（68 个文件，7092 行）
> 建表语句：`db/schema.sql` 中 `cross_border_*` 共 8 张表
> 接口入口：`http://127.0.0.1:8090/api/crossborder/**`（6 个控制器，38 个接口）

本文以**业务为主线**，把跨境支付的领域知识讲清楚，再落到本项目每一行关键实现，最后给出可直接复制的实操命令。文中所有数字都是实测结果，不是估算。

---

## 一、跨境支付难在哪

境内转账只有一个问题：把钱从 A 账户挪到 B 账户。跨境支付要把这件事拆成五个各自独立、又必须串起来的难题：

| 难题 | 境内支付 | 跨境支付 |
|---|---|---|
| 币种 | 同一种货币，金额可直接相加 | 两端币种不同，必须换汇；换汇就有汇率风险 |
| 时间 | 实时或准实时到账 | SWIFT 动辄 2 天，本地清算也要分钟级 |
| 合规 | 反欺诈即可 | 制裁名单、KYC 等级、反洗钱申报，缺一项就是监管处罚 |
| 通道 | 央行清算系统，唯一 | SWIFT / CIPS / 本地清算网络，成本与时效差异巨大 |
| 对账 | 单边账 | 必须与渠道回单双向核对，长款短款都要追 |

**这五个难题决定了本模块的五条主线**：锁汇（汇率）、清算批次（时间）、合规筛查（监管）、渠道路由（通道）、对账（核对）。理解了这个背景，再看代码就不会迷失在细节里。

---

## 二、一笔汇款要走过哪些阶段

```
                    ┌─────────────────────────────────────────┐
① 开户 → ② 询价 → ③ 合规筛查 → ④ 锁汇 → ⑤ 扣款记账 ──┐
                                                        │
                    ┌───────────────────────────────────┘
                    ▼
              ⑥ 发清算消息 ──→ ⑦ 收款方入账 ──→ ⑧ 归入批次 ──→ ⑨ 对账
                    │
                    └─→ 失败则由定时任务补偿重发（最终一致）
```

逐阶段说明：

| 阶段 | 做什么 | 关键约束 |
|---|---|---|
| ① 开户 | 建立账户，设定币种、KYC 等级、单笔/日累计限额 | 跨境汇款要求**两端币种不同**，同币种直接拒绝 |
| ② 询价 | 生成带有效期的汇率报价（bid/ask） | 报价有过期时间，过期不能再锁 |
| ③ 合规筛查 | 制裁名单、KYC 等级、反洗钱、限额四道检查 | 任一道 REJECT 则整笔拒绝；AML 命中进入人工审核 |
| ④ 锁汇 | 把报价锁定到本笔汇款，汇率从此固定 | 并发锁同一报价只有一个能成功 |
| ⑤ 扣款记账 | 扣付款方余额、记流水、推进状态 | 三者**同一事务**，任一步失败整体回滚 |
| ⑥ 发消息 | 事务提交后才投递清算消息 | 先发消息后提交事务，会出现"回滚了但下游已收到" |
| ⑦ 入账 | 收款方加余额、记流水、推进到已结算 | 幂等，重复消息不能重复入账 |
| ⑧ 归批 | 把汇款单归入清算批次 | 不归批的对账时会成为盲区 |
| ⑨ 对账 | 与渠道回单逐笔比对 | 识别长款、短款、金额不符 |

---

## 三、核心概念速查

### 3.1 外汇相关

| 术语 | 含义 | 本项目实现 |
|---|---|---|
| 中间价（mid rate） | 买卖价的平均，市场参考价 | 以美元为桥做**交叉计算**：`CNY/USD = USD汇率 / CNY汇率` |
| 点差（spread） | 买卖价之差，银行利润来源 | 固定 0.3%，`ask = mid × 1.0015`，`bid = mid × 0.9985` |
| 锁汇 | 把某笔交易的汇率固定下来，锁定后不受市场波动影响 | `FxQuote.lock()`，成功后 `locked_rate` 写入汇款单 |
| 敞口（exposure） | 已锁汇但尚未清算完成的净头寸 | 汇总 `QUOTE_LOCKED / FUNDS_DEBITED / SETTLING` 三种状态的单子 |
| 浮动盈亏 | 锁定汇率与当前市价的差 × 锁定量 | `lockedNotional × (avgLockedRate − currentRate)`，正为盈负为亏 |

实测一次 CNY→USD 询价（有效期 300 秒）：

```
bidRate = 0.13965035   askRate = 0.14006993
```

推导：中间价 `1.0 / 7.15 = 0.139860`，ask `= 0.139860 × 1.0015 = 0.140070`。客户换汇按 **ask** 成交，所以到手金额比按中间价算的少，少的那部分就是银行的。

### 3.2 合规与风控

| 术语 | 含义 | 本项目判定 |
|---|---|---|
| 制裁名单 | 联合国/各国公布的禁止交易主体 | Redis Set，命中即 REJECT。Redis 不可用时**按命中处理**（宁可误拒不可漏放） |
| KYC 等级 | 客户身份核验深度，决定可交易额度 | 0 级 1000 / 1 级 10000 / 2 级 100000 / 3+ 级 1000 万 |
| 反洗钱（AML） | 大额与可疑交易监控 | 超过 50000 挂**人工审核**，不是直接拒绝——大额交易本身合法 |
| 拆分交易（structuring） | 为规避申报把大额拆成多笔小额 | 累计达申报线、每笔都在线下、笔数 ≥ 3 即告警 |
| 申报线 | 大额交易必须报告的门槛 | 10000（中美均为该量级） |

### 3.3 清算渠道

| 渠道 | 到账 | 手续费 | 单笔上限 | 适用场景 |
|---|---|---|---|---|
| SWIFT | 2880 分钟（2 天） | 50 + 0.1% | 1 亿 | 覆盖最广，走代理行，贵且慢 |
| CIPS | 60 分钟 | 10 + 0.05% | 5000 万 | 人民币跨境清算，便宜且快 |
| LOCAL | 30 分钟 | 5 + 0.02% | **30 万** | 各国本地清算网络，最便宜但额度受限 |

路由打分：`score = 手续费 + 到账分钟数 × 时效权重`（普通 0.01，加急 0.05）。

实测汇 20000 元的评分明细：

| 渠道 | 手续费 | 时效成本 | 总分 | 是否合格 |
|---|---|---|---|---|
| SWIFT | 70.00 | 28.80 | 98.80 | 是 |
| CIPS | 20.00 | 0.60 | 20.60 | 是 |
| LOCAL | 9.00 | 0.30 | **9.30** | 是（推荐） |

加急时时效权重放大 5 倍，SWIFT 的 28.8 分变成 144 分，CIPS 与 LOCAL 的优势进一步拉开——这就是"加急要加钱"的量化依据。

### 3.4 对账差异

| 差异类型 | 含义 | 枚举值 |
|---|---|---|
| 金额不符 | 本地与渠道金额不一致，多为中间行扣费或汇率精度差 | `AMOUNT_MISMATCH(3)` |
| 渠道漏单 | 本地已结算但渠道回单没有 | `MISSING_IN_CHANNEL(4)` |
| 渠道多单 | 渠道回单有但本地没有，最危险的一种 | `MISSING_IN_LOCAL(5)` |

`LONG(1)` 与 `SHORT(2)` 两个枚举定义了但未使用——实际判定用上面三种。

---

## 四、数据模型

8 张表，按业务职责分四组：

### 4.1 账户组

| 表 | 用途 | 设计要点 |
|---|---|---|
| `cross_border_account` | 账户主体 | `balance` 与 `frozen_balance` 分离；`kyc_level` 决定额度；`status` 1 激活 2 冻结 |
| `cross_border_account_event` | 冻结/解冻留痕 | **只增不改**，无更新接口。状态回答"现在能不能用"，事件回答"怎么变成这样的" |

### 4.2 汇兑组

| 表 | 用途 | 设计要点 |
|---|---|---|
| `cross_border_fx_quote` | 汇率报价 | 同时存 `bid_rate` 与 `ask_rate`；`status` 1 可用 2 已锁 3 已用 4 已过期；有 `expire_time` 索引支撑定时清理 |

### 4.3 交易组

| 表 | 用途 | 设计要点 |
|---|---|---|
| `cross_border_remittance` | 汇款单 | 三个关键索引：`uk_idempotent_key` 幂等兜底、`idx_status` 补偿扫描、`idx_batch_no` 对账归集 |
| `cross_border_compliance_record` | 合规检查记录 | 每道检查一行，**只增不改**，是监管检查时"这笔交易审过了"的直接证据 |
| `cross_border_account_ledger` | 资金流水 | `uk_remittance_account_direction` 唯一索引——同一笔汇款同一账户同一方向只能有一条，这是防重复入账的最后一道防线 |

### 4.4 清算对账组

| 表 | 用途 | 设计要点 |
|---|---|---|
| `cross_border_settlement_batch` | 清算批次 | `status` 1 开启 2 已关闭 3 已结算；`cutoff_time` 是清算窗口截止 |
| `cross_border_recon_diff` | 对账差异 | `handle_status` 标记处理进度，支持单条与批量处理 |

**流水的唯一索引是最值得注意的设计**。它让"重复入账"这个资金系统最忌讳的事故，在最底层被数据库挡住——应用层即使有 bug，也只能撞出唯一键冲突让事务回滚，而不是悄悄多加一笔钱。

---

## 五、状态机：汇款单的一生

```
                    ┌──────────────────────────────┐
                    │                              │
   CREATED ──→ QUOTE_LOCKED ──→ FUNDS_DEBITED ──→ SETTLING ──→ SETTLED ✓
       │              │               │                            │
       │              │               │                            │
       ├──→ COMPLIANCE_REJECTED ✗     └──→ FAILED ──→ REFUNDED ✓   │
       │              │                                            │
       └──→ PENDING_REVIEW ──→ (放行) QUOTE_LOCKED                 │
                      └────→ (驳回) COMPLIANCE_REJECTED ✗           │
```

**九种状态**（`RemittanceStatus`）：

| 状态 | 编码 | 含义 | 是否终态 |
|---|---|---|---|
| `CREATED` | 1 | 已创建 | 否 |
| `COMPLIANCE_REJECTED` | 2 | 合规拒绝 | 是 |
| `QUOTE_LOCKED` | 3 | 已锁汇 | 否 |
| `FUNDS_DEBITED` | 4 | 已扣款 | 否 |
| `SETTLING` | 5 | 清算中 | 否 |
| `SETTLED` | 6 | 已结算 | 是 |
| `FAILED` | 7 | 失败 | 是 |
| `REFUNDED` | 8 | 已退款 | 是 |
| `PENDING_REVIEW` | 9 | 待人工审核 | 否 |

**一条最重要的设计原则：失败一律走退款，绝不回退。**

资金一旦划出，回退就会产生"在途资金"——钱已从付款方扣掉，却没有对应的收款方入账，这笔钱在账上悬空。所以失败路径是 `FAILED → REFUNDED`，而不是把状态改回 `FUNDS_DEBITED`。

唯一的例外是审核放行中途失败：那时资金**尚未扣减**，回退到 `PENDING_REVIEW` 让审核员重新处理是安全的，不会造成误伤。

**并发防护**全部落在这一句 SQL 上：

```sql
update cross_border_remittance
set status = #{target}, version = version + 1, update_time = now()
where remittance_no = #{remittanceNo}
  and status = #{expectedStatus}
  and version = #{version}
```

`status = 期望状态` 保证不会跳过中间步骤，`version = 当前版本` 保证不会覆盖别人的写入，两者缺一不可。

---

## 六、合规筛查的四道检查

`ComplianceServiceImpl.screen()` 依次跑完四道检查，取最严重的结论：任一 REJECT 则整体 REJECT；无 REJECT 但有 MANUAL_REVIEW 则整体挂人工审核。

| 顺序 | 检查项 | 判定 | 失败后果 |
|---|---|---|---|
| 1 | 制裁名单 | 户主名在 Redis Set 中 | REJECT |
| 2 | KYC 等级 | 金额 ≤ 等级对应额度 | REJECT |
| 3 | 反洗钱 | 金额 ≤ 50000 | 超限 → MANUAL_REVIEW |
| 4 | 限额 | 单笔限额 + 日累计限额 | REJECT |

### 6.1 一个必须知道的坑：人工审核分支默认不可达

开户时如果不指定限额，`CrossBorderAccountServiceImpl` 会给默认值：

```java
dailyLimit  = 100000 （未指定时）
singleLimit = 50000  （未指定时）
```

而反洗钱的人工审核阈值恰好也是 **50000**。两者相等意味着：金额超过 5 万时，第 4 道限额检查先判 REJECT，第 3 道根本轮不到返回 MANUAL_REVIEW。

实测：默认账户汇 60000 → `remittance rejected by compliance check`（被限额拒绝）。

**想看到人工审核分支，开户时必须把单笔限额提到 5 万以上**：

```bash
curl -X POST 'http://127.0.0.1:8090/api/crossborder/accounts' -H 'Content-Type: application/json' \
  -d '{"ownerName":"Carol","country":"CN","currency":"CNY","balance":500000,"kycLevel":2,"singleLimit":200000,"dailyLimit":500000}'
```

再汇 60000，才会得到 `remittance suspended for manual review`。合规记录里能看到三行判定：

```
SANCTION  PASS
KYC       PASS
AML       MANUAL_REVIEW   amount exceeds manual review threshold 50000
```

这个坑的价值在于：它演示了**多道检查的执行顺序会互相掩盖**。真实系统里这类"规则打架"的问题要靠参数评审发现，测试往往覆盖不到。

### 6.2 日累计限额必须用 Lua 保证原子性

```lua
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local delta = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local next = current + delta
redis.call('SET', KEYS[1], tostring(next))
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
if next <= limit then
    return 1
end
return 0
```

**累加与判断必须在同一个脚本内完成**。若拆成"先 GET 再 SET"两次调用，两次之间就是竞态窗口——并发汇款会各自读到旧值，日限额形同虚设。

金额以**分**为单位传入，避免 Lua 里的浮点精度问题。

被拒绝的汇款会调用 `releaseDailyLimit()` 释放占用。这一步不做的话，客户失败几次之后当天就一笔都汇不出去了——这是真实系统里最常见的额度泄漏事故。

### 6.3 拆分交易检测

规则与申报制度对齐：**累计达到申报线、且每笔都在线下、笔数达到门槛**。单笔超线的交易本身会正常申报，不算拆分。

实测连续 3 笔 9000 元后：

```json
{"underLineCount":3,"payerAccountId":9,"totalAmount":27000.00,"totalCount":3}
```

第 3 笔触发告警，`GET /risk/aml/flagged` 能查到命中账户。

---

## 七、关键工程实现

### 7.1 幂等的三层防护

| 层次 | 做法 | 挡住什么 |
|---|---|---|
| 第一层 | 先查幂等键，命中直接返回原单 | 绝大多数正常重试 |
| 第二层 | 分布式锁内**双查**，锁内再查一次 | 并发的同一幂等键请求 |
| 第三层 | 唯一索引 `uk_idempotent_key`，冲突时返回已存在的单子 | 兜底，防止前两层都漏掉 |

第三层尤其关键：唯一索引冲突时**不能抛异常**，而要返回那笔已存在的单子。否则客户端重试会收到冲突错误，误以为汇款失败又发起一次。

```java
try {
    return doCreate(request);
} catch (DuplicateKeyException ex) {
    return onDuplicateIdempotentKey(request.getIdempotentKey());
}
```

### 7.2 事务边界：消息必须在提交后发

```java
if (TransactionSynchronizationManager.isSynchronizationActive()) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
} else {
    task.run();
}
```

如果不等 `afterCommit` 就发消息，事务一旦回滚，下游已经收到通知去放款，钱就出去了。这个错误的代价是真实的资金损失。

**消息发送失败不回滚**：资金已在本地扣掉，回滚才是错的，留下 `FUNDS_DEBITED` 状态的单子交给定时任务重推才是对的。这是最终一致的标准处理。

### 7.3 事务代理失效的坑

`CrossBorderSettlementHandler` 的注释里记着一次真实事故：

> 这里的入账必须走独立 bean 而不是本类内部方法：同类 this 调用绕过 Spring 代理，事务静默失效，曾经因此出现并发消息重复入账六次的事故。

所以入账逻辑被抽到 `CrossBorderLedgerService` 这个独立 bean 里。`@Transactional` 只有被代理对象调用时才生效，`this.xxx()` 是绕过代理的普通方法调用。

### 7.4 消息补偿的静默期

定时任务每 30 秒扫描一次 `FUNDS_DEBITED` 的单子重发消息，但**创建 2 分钟内不补偿**：

```java
private static final Duration COMPENSATION_IDLE = Duration.ofMinutes(2);
```

不加静默期，刚创建的单子消息还在投递途中，每轮扫描都会把在途消息再发一遍，形成消息风暴。消费端幂等，重复发送不会重复入账，所以重发本身是安全的。

### 7.5 对账为什么要按批次

批次 `OPEN` 时拒绝对账——还在收集，对账结果会一直变。只有 `CLOSED` 或 `SETTLED` 才能对账，且对账前先清旧差异，保证对账本身是幂等的，不会越跑差异越多。

---

## 八、接口清单

| 控制器 | 路径前缀 | 接口数 | 职责 |
|---|---|---|---|
| `CrossBorderAccountController` | `/api/crossborder` | 11 | 开户、查询、冻结解冻、事件历史、制裁名单 |
| `CrossBorderFxController` | `/api/crossborder/fx` | 5 | 询价、查报价、可用报价、当前中间价、清理过期 |
| `CrossBorderRemittanceController` | `/api/crossborder/remittance` | 10 | 汇款、查询、合规记录、人工审核、运行时统计 |
| `CrossBorderSettlementController` | `/api/crossborder/settlement` | 8 | 批次管理、归集、清算、关闭到期 |
| `CrossBorderReconController` | `/api/crossborder/recon` | 6 | 对账执行、回单模拟、报告、差异处理 |
| `CrossBorderRiskController` | `/api/crossborder/risk` | 6 | 渠道路由、AML 画像、可疑账户、汇率敞口 |

完整列表见 Knife4j：`http://127.0.0.1:8090/doc.html`。

---

## 九、实操：跑通完整链路

以下命令可直接复制执行。**注意第 3 步的限额设置**，否则人工审核分支走不到。

```bash
B=http://127.0.0.1:8090/api/crossborder

# 1. 开立两个不同币种的账户（收款方币种必须与付款方不同）
curl -X POST $B/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Alice","country":"CN","currency":"CNY","balance":100000,"kycLevel":2,"singleLimit":200000,"dailyLimit":500000}'
curl -X POST $B/accounts -H 'Content-Type: application/json' \
  -d '{"ownerName":"Bob","country":"US","currency":"USD","balance":0,"kycLevel":2}'

# 2. 询价，返回带有效期的报价（记下 quoteNo）
curl -X POST "$B/fx/quote?sourceCurrency=CNY&targetCurrency=USD&validSeconds=300"

# 3. 先建一个清算批次（cutoff=0 便于后续对账），记下 batchNo
curl -X POST "$B/settlement/batch?channel=SWIFT&currency=USD&cutoffMinutes=0"

# 4. 小额汇款，指定 SWIFT 渠道以便归入上面的批次
curl -X POST $B/remittance -H 'Content-Type: application/json' \
  -d '{"idempotentKey":"demo-1","payerAccountNo":"CB...","payeeAccountNo":"CB...","sourceAmount":1000,"quoteNo":"FQ...","channel":"SWIFT"}'

# 5. 大额汇款，应挂起人工审核（code=1002 suspended for manual review）
curl -X POST $B/remittance -H 'Content-Type: application/json' \
  -d '{"idempotentKey":"demo-2","payerAccountNo":"CB...","payeeAccountNo":"CB...","sourceAmount":60000}'

# 6. 查待审核列表 → 审核放行
curl $B/remittance/pending-review
curl -X POST "$B/remittance/RM.../review/approve" -H 'Content-Type: application/json' \
  -d '{"reviewer":"compliance-dong","note":"material verified"}'

# 7. 幂等重放：同一个 idempotentKey 再发一次，应返回同一张单
#    运行时统计里 idempotentHit 会 +1

# 8. 冻结收款账户 → 查事件历史 → 重复冻结应报冲突
curl -X POST "$B/accounts/CB.../freeze?reason=aml investigation&operator=risk-team"
curl "$B/accounts/CB.../events"
curl -X POST "$B/accounts/CB.../freeze?reason=dup&operator=risk-team"   # → code=1002 冲突
curl -X POST "$B/accounts/CB.../unfreeze?reason=closed&operator=risk-team"

# 9. 关闭到期批次 → 执行对账（注入 20% 渠道差错）
curl -X POST "$B/settlement/close-overdue"
curl -X POST "$B/recon/SB...?simulatedErrorRate=0.2"

# 10. 资金自检：用流水反推余额，与实际余额比对
curl "$B/accounts/CB.../diff?initial=100000"
# → {"diff":0.00,"consistent":true} 表示账实相符

# 11. 风控观察
curl "$B/risk/route?amount=20000&urgent=false"        # 渠道路由评分
curl "$B/risk/aml/profile?payerAccountId=7"           # 当日交易画像
curl "$B/risk/aml/flagged"                            # 命中拆分嫌疑的账户
curl "$B/risk/fx-exposure"                            # 汇率敞口

# 12. 运行时统计
curl $B/remittance/runtime
```

### 实测结果

| 验证项 | 结果 |
|---|---|
| 小额汇款 | 成功，状态推进到 `SETTLED` |
| 大额汇款（限额已调高） | `suspended for manual review`，合规记录显示 AML 判定为 MANUAL_REVIEW |
| 审核放行 | 成功，继续走锁汇扣款清算 |
| 幂等重放 | 返回同一张单，`idempotentHit` 计数 +1 |
| 冻结 / 解冻 | 成功，事件表写入 FREEZE 记录 |
| 重复冻结 | `code=1002` 冲突，未重复写事件 |
| 制裁名单 | 加入后汇款被拒，移除后恢复 |
| 拆分交易 | 3 笔 9000 后 `underLineCount=3`，进入 flagged 列表 |
| 资金自检 | `diff=0.00, consistent=true`，账实相符 |
| 对账 | `localCount=1, matchedCount=1, balanced=true` |

---

## 十、观察不到的角落与已知限制

学习时知道"哪里看不到东西"，比知道"哪里能看到东西"更重要。

### 10.1 汇率敞口几乎总是空的

敞口统计的是 `QUOTE_LOCKED / FUNDS_DEBITED / SETTLING` 三种状态的单子。由于实时清算链路在毫秒级完成，汇款单在这三个状态停留的时间极短，所以 `GET /risk/fx-exposure` 通常返回空数组。

想观察敞口，需要人为制造延迟，比如暂时停掉消息消费，或把 MQ 切到投递较慢的通道。这不是 bug，而是**实时链路效率太高导致观察窗口消失**。

### 10.2 AML 贴线金额明细会去重

Redis 里用 Set 存贴线金额：

```lua
redis.call('SADD', KEYS[1] .. ':under', ARGV[1])
```

三笔金额相同的 9000 元，Set 里只会有一条记录。`underLineCount`（笔数）是准的，但 `underLineAmounts`（金额明细）看不出重复。若需要完整明细，应改用 List 或加上时间戳后缀。

### 10.3 汇率是静态表

`USD_RATES` 是写死的常量表，不接实时行情。重点在演示**锁汇机制本身**，而不是汇率数据的真实性。真实系统由交易系统实时推送牌价。

### 10.4 与真实系统的差距

| 方面 | 本项目 | 真实系统 |
|---|---|---|
| 汇率 | 静态表 + 固定点差 | 实时行情，点差随币种与金额浮动 |
| 渠道 | 三个常量，模拟评分 | 对接真实清算网络，含流动性管理 |
| 合规 | 四类规则，Redis 名单 | 对接外部名单服务，规则引擎上千条 |
| 对账 | 模拟生成渠道回单 | 真实下载渠道对账文件，格式各异 |
| 账务 | 单币种分户，简单借贷 | 多币种分账、内部户、头寸管理 |
| 报文 | 无 | SWIFT MT103 / ISO 20022 报文 |

---

## 十一、延伸学习建议

想从"会用"到"真懂"，建议按这个顺序深入：

1. **先跑通再读码**——按第九节把链路跑一遍，观察 `runtime` 接口里每个计数器的变化，再回头看代码，会知道每段逻辑对应哪一次计数。
2. **重点读三个类**——`RemittanceServiceImpl`（主链路与并发）、`ComplianceServiceImpl`（风控与 Lua）、`CrossBorderLedgerServiceImpl`（事务与账务）。这三个类是全模块的核心。
3. **做破坏性实验**——停掉 Redis 看制裁检查如何 fail-safe；停掉 MQ 看补偿任务如何把单子推下去；把 `singleLimit` 调到阈值以下看人工审核分支如何消失。
4. **对照真实业务**——找一份 SWIFT MT103 报文样本，对比本项目汇款单的字段，看少了什么（报文头、中间行、费用承担方式 SHA/BEN/OUR 等）。
5. **补上缺失的一环**——本项目没有实现"退汇"（收款失败后原路退回）。可以试着基于现有状态机加一条 `SETTLING → RETURNING → RETURNED` 的分支，这是检验是否真懂状态机的最好练习。
