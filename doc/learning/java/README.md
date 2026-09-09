# Java 知识点

> Java 语言与并发相关的知识点整理。
> 写法统一：先说解决什么问题，再说机制，最后给判断标准。

---

## 并发

| 文档 | 主题 |
|---|---|
| [volatile：一句话和两个坑](volatile.md) | 可见性、禁止重排序、**不保证原子性** |
| [synchronized：锁升级](synchronized.md) | 偏向→轻量→重量、与 Lock 怎么选 |
| [CAS](cas.md) | 乐观锁原理、ABA 与版本号、自旋开销 |
| [AQS](aqs.md) | 一个 state + 一个队列，JUC 的骨架 |
| [线程池](threadpool.md) | 七个参数、任务处理流程、为什么不用 Executors |
| [ThreadLocal](threadlocal.md) | 泄漏的真实原因、线程池里数据串了 |
| [ConcurrentHashMap](concurrenthashmap.md) | 分段锁到 CAS 的演进、读为什么不加锁 |

---

## 建议阅读顺序

`volatile` → `synchronized` → `cas` → `aqs` → `threadpool` → `threadlocal` → `concurrenthashmap`

前四篇递进：volatile 解决可见性 → synchronized 补上原子性 → CAS 是 synchronized 底层的轻量手段
→ AQS 用 CAS 搭起整个 JUC。后三篇相对独立，按需阅读。
