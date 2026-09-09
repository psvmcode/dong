# 线程池：七个参数和那个要命的队列

> 目标：说清线程池怎么处理一个新任务、七个参数怎么配、以及为什么不推荐 Executors。

---

## 一句话

**线程池就是「一组可以复用的线程 + 一个装任务的队列」，避免为每个任务都新建/销毁线程。**

线程创建和销毁是有成本的（涉及操作系统资源），
而且无限制创建线程会把内存和 CPU 撑爆。线程池解决的就是这两件事。

---

## 处理一个新任务的流程

**这段流程必须背下来，面试必考**：

```
提交任务
  ↓
① 当前线程数 < corePoolSize ?
   → 是：新建核心线程执行
   → 否 ↓
② 工作队列没满 ?
   → 是：放进队列等着
   → 否 ↓
③ 当前线程数 < maximumPoolSize ?
   → 是：新建临时线程执行（救急）
   → 否 ↓
④ 执行拒绝策略
```

**关键理解**：

- **核心线程是常驻的**，即使空闲也不销毁（除非开了 `allowCoreThreadTimeOut`）
- **队列满了才会创建临时线程**——这是最容易记反的地方
- 临时线程空闲超过 `keepAliveTime` 就会被回收

---

## 七个参数

```java
public ThreadPoolExecutor(
    int corePoolSize,              // ① 核心线程数
    int maximumPoolSize,           // ② 最大线程数
    long keepAliveTime,            // ③ 临时线程空闲存活时间
    TimeUnit unit,                 // ④ 时间单位
    BlockingQueue<Runnable> workQueue,   // ⑤ 任务队列
    ThreadFactory threadFactory,   // ⑥ 线程工厂
    RejectedExecutionHandler handler     // ⑦ 拒绝策略
)
```

| 参数 | 说明 |
|---|---|
| `corePoolSize` | 核心线程数，常驻不销毁 |
| `maximumPoolSize` | 最大线程数 = 核心 + 临时 |
| `keepAliveTime` | **临时线程**空闲多久后回收 |
| `unit` | 时间单位 |
| `workQueue` | 存放等待任务的阻塞队列 |
| `threadFactory` | 用来创建线程，**建议自定义给线程起有意义的名字** |
| `handler` | 队列满了且线程数达上限时的处理策略 |

### 三种常见队列

| 队列 | 特点 | 风险 |
|---|---|---|
| `ArrayBlockingQueue` | **有界**数组队列 | 安全，推荐 |
| `LinkedBlockingQueue` | **默认无界**（容量 Integer.MAX_VALUE） | **可能 OOM** |
| `SynchronousQueue` | **不存任务**，来了必须立刻交给线程 | 需要很大的 maximumPoolSize |

`LinkedBlockingQueue` 不指定容量就是无界的——
任务堆积时会一直往里加，直到内存耗尽。**这是最常见的线上事故之一。**

### 四种拒绝策略

| 策略 | 行为 |
|---|---|
| `AbortPolicy`（默认） | **抛异常** `RejectedExecutionException` |
| `CallerRunsPolicy` | 让**提交任务的线程自己执行**（天然的反压，能减缓提交速度） |
| `DiscardPolicy` | **静默丢弃**，不抛异常——最危险，丢了都不知道 |
| `DiscardOldestPolicy` | 丢弃队列里**最老**的任务，然后重试提交 |

**生产建议**：用 `CallerRunsPolicy` 或自定义策略（记录日志 + 告警）。
`DiscardPolicy` 千万别用，静默丢任务排查起来非常痛苦。

---

## 为什么不推荐 Executors

阿里开发手册明确要求：**不允许用 `Executors` 创建线程池**。

原因不是它不好用，而是它**把风险藏起来了**：

| 方法 | 问题 |
|---|---|
| `newFixedThreadPool` | 用的是**无界** `LinkedBlockingQueue` → 任务堆积 → **OOM** |
| `newSingleThreadExecutor` | 同上，无界队列 |
| `newCachedThreadPool` | `maximumPoolSize` 是 `Integer.MAX_VALUE` → 无限建线程 → **OOM** |
| `newScheduledThreadPool` | 无界延迟队列 → **OOM** |

**正确做法**：直接用 `ThreadPoolExecutor` 构造函数，明确指定每个参数。

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        4,                              // 核心线程数
        8,                              // 最大线程数
        60L, TimeUnit.SECONDS,          // 临时线程存活时间
        new ArrayBlockingQueue<>(1000), // 有界队列，容量明确
        new ThreadFactory() {           // 自定义线程名，排查问题时救命
            private final AtomicInteger n = new AtomicInteger(1);
            public Thread newThread(Runnable r) {
                return new Thread(r, "biz-pool-" + n.getAndIncrement());
            }
        },
        new ThreadPoolExecutor.CallerRunsPolicy()   // 拒绝策略
);
```

**为什么自定义线程名很重要**：出问题时 dump 线程栈，
看到的是 `pool-1-thread-3` 还是 `biz-pool-3`，
排查效率差一个数量级。

---

## 线程数怎么配

没有万能公式，但有个思路：

| 任务类型 | 建议 |
|---|---|
| **CPU 密集型**（计算、加密、反序列化） | 线程数 ≈ **CPU 核数** 或 +1 |
| **IO 密集型**（网络请求、数据库查询） | 线程数可以**远大于**核数，因为线程大部分时间在等 IO |

IO 密集型的一个经验公式：

```
线程数 = CPU 核数 × (1 + 等待时间 / 计算时间)
```

比如等待 100ms、计算 10ms，4 核：
`4 × (1 + 100/10) = 44`。

**但这只是起点**，真实值要靠压测调整。
而且一个应用里往往有多个线程池，总数要一起考虑。

---

## 常见坑

**坑一：用无界队列，maximumPoolSize 形同虚设**

`newFixedThreadPool(10)` 的核心和最大都是 10，队列无界。
永远不会创建临时线程，任务只会一直堆进队列 → OOM。

**记住**：**只有队列满了，maximumPoolSize 才有意义**。

**坑二：线程池里的异常被吞掉**

`execute()` 提交的任务，如果抛出未捕获异常，
**线程会死掉**，但你可能完全不知道（没有日志）。

```java
executor.execute(() -> {
    throw new RuntimeException("boom");   // 会在 stderr 打印，但容易忽略
});
```

**解决**：

1. 用 `submit()` 提交，异常会封装在 `Future` 里，`get()` 时抛出
2. 或在任务里 `try-catch` 全包住并记日志
3. 或自定义 `ThreadFactory` 设置 `UncaughtExceptionHandler`

**坑三：ThreadLocal 与线程池共用导致数据串了**

线程复用，上次任务留在 `ThreadLocal` 里的值会带到下次任务。
用完必须 `remove()`，详见 [threadlocal.md](threadlocal.md)。

**坑四：忘记关闭线程池**

应用退出时 `shutdown()`，否则 JVM 无法退出（非守护线程还在跑）。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 任务处理顺序 | 核心线程 → 队列 → 临时线程 → 拒绝策略 |
| 何时建临时线程 | **队列满了**才建（最容易记反） |
| maximumPoolSize 何时有效 | 队列有界时才有意义 |
| 为什么不用 Executors | 无界队列或无限线程 → **OOM** |
| CPU 密集型怎么配 | ≈ CPU 核数 |
| IO 密集型怎么配 | 可远大于核数，靠压测定 |
| 拒绝策略选哪个 | `CallerRunsPolicy` 或自定义告警，别用静默丢弃 |
