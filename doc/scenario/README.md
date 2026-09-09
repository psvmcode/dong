# 场景文档

> 本目录按**业务场景**组织，每个场景一份文档，覆盖「业务背景 + 技术设计 + 接口清单 + 数据模型」。
> 适合想了解某个模块全貌时从头读。

---

## 与上层 doc 的区别

| 位置 | 定位 | 读者 |
|---|---|---|
| `doc/scenario/`（本目录） | **模块全景**，按场景横向铺开 | 想快速掌握一个模块 |
| [`../topic/`](../topic/README.md) | **专题深挖**，就一个主题讲透 | 想深入理解某处设计取舍 |

例如跨境支付：先看本目录的 [crossborder.md](crossborder.md) 建立整体认识，
再按需读 [`../topic/crossborder-payment.md`](../topic/crossborder-payment.md)（业务深度）
或 [`../topic/crossborder-interview-prep.md`](../topic/crossborder-interview-prep.md)（面试向）。

---

## 文档清单

### [经典 Redis 场景模块说明](classic.md)

`com.dong.classic`，接口前缀 `/api/classic`。

| 场景 | 核心结构 |
|---|---|
| 短链接 | 发号器 + Base62 + 缓存 |
| 用户签到 | Bitmap |
| 独立访客 | HyperLogLog |
| 排行榜 | ZSet |
| 延迟队列 | RDelayedQueue |
| 地理位置 | GEO |
| 发号器 | 四种策略对比 |
| 锁与限流 | 分布式锁 / 令牌桶 |

贯穿原则：**Redis 负责高性能，数据库负责可靠性**。
详见文档 [三、技术场景](classic.md#三技术场景)。

### [跨境支付模块说明](crossborder.md)

`com.dong.crossborder`，接口前缀 `/api/cross-border`。

覆盖报价、锁汇、汇款、清算、合规校验、退汇全流程，
含状态机、幂等三层、对账等资金系统的核心设计。

---

## 通用约定

两份文档都遵循同一套结构：模块概览 → 业务场景 → 技术场景 → 接口文档 → 数据模型 → 本地验证。

涉及数据库改动时：

- 唯一权威源是 [`../../db/schema.sql`](../../db/schema.sql)
- 改完必须执行 `../../deploy/gen-initdb.sh` 重新生成 `../../deploy/initdb/`
- **不要手工编辑** `deploy/initdb/` 下的文件——它们是自动生成物
