# MySQL 知识点

> MySQL 索引、事务、锁相关的知识点整理。
> 写法统一：先说解决什么问题，再说机制，最后给判断标准。

---

## 文档清单

| 文档 | 主题 |
|---|---|
| [索引：为什么是 B+ 树](index.md) | B+ 树选型理由、回表、覆盖索引、最左前缀 |
| [事务隔离](transaction-isolation.md) | 脏读/不可重复读/幻读、四个级别怎么应对 |
| [MVCC](mvcc.md) | 快照读与当前读、undo log 版本链、ReadView |
| [锁](lock.md) | 行锁/间隙锁、锁全表的坑、死锁排查 |
| [执行计划](explain.md) | type 各级别、索引失效的九种情况 |

---

## 建议阅读顺序

`index` → `transaction-isolation` → `mvcc` → `lock` → `explain`

前三篇是递进关系：**隔离级别**定义了要解决的问题，**MVCC** 是 InnoDB 给出的答案，
**锁**配合 MVCC 补上当前读的缺口。`explain` 相对独立，随时可读。
