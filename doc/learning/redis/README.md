# Redis 知识点

> Redis 持久化、缓存设计、分布式锁相关的知识点整理。
> 写法统一：先说解决什么问题，再说机制，最后给判断标准。

---

## 文档清单

| 文档 | 主题 |
|---|---|
| [持久化](persistence.md) | RDB 快照、AOF 日志、混合持久化、为什么持久化不等于备份 |
| [缓存一致性](cache-consistency.md) | 穿透/击穿/雪崩、先删缓存还是先改库 |
| [分布式锁](distributed-lock.md) | SET NX PX、看门狗、Redlock 争议 |

---

## 建议阅读顺序

`persistence` → `cache-consistency` → `distributed-lock`

三者相对独立，按需阅读。做缓存设计先看第二篇，用锁看第三篇。

---

## 一条贯穿的原则

**Redis 是加速层，不是权威数据源。**

凡是丢了会影响业务的数据，都要有数据库兜底——
这与 [`scenario/classic.md`](../../scenario/classic.md) 里
「Redis 负责高性能，数据库负责可靠性」是同一个判断标准。
