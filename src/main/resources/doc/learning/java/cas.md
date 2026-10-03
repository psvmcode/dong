# CAS：乐观锁的底層，以及 ABA 到底怎么回事

> 目标：说清 CAS 的原理、三个问题（ABA、自旋开销、只能一个变量），以及怎么解决。

---

## 一句话

**CAS 就是「我认为值应该是 A，如果是，就改成 B；如果不是，说明被人改过了，我重试」。**

它是一**乐观**策略：不加锁，假设没人跟我抢，抢了就重试。
对比 `synchronized` 的**悲观**策略：先加锁，确保没人能抢。

---

## 三个操作数

```java
CAS(V, A, B)
```

| 符号 | 含义 |
|---|---|
| `V` | 要更新的**内存地址** |
| `A` | **预期的原值**（我认为它现在应该是多少） |
| `B` | **要写入的新值** |

**逻辑**：如果 `V` 处的真实值等于 `A`，就把它改成 `B`，返回成功；
否则什么都不做，返回失败。

**整个操作是一条 CPU 指令**（x86 上是 `cmpxchg`），**原子**，不会被打断。

伪代码（帮助理解，实际是一条指令）：

```java
if (V == A) {
    V = B;
    return true;
} else {
    return false;   // 说明被别的线程改过了
}
```

---

## Java 里怎么用

Java 的 CAS 在 `sun.misc.Unsafe` 类里（底层是 native 方法），
但我们一般不直接用，而是用 `java.util.concurrent.atomic` 包：

```java
AtomicInteger count = new AtomicInteger(0);

count.incrementAndGet();   // 内部就是 CAS 自旋
```

`incrementAndGet()` 的近似实现：

```java
public final int incrementAndGet() {
    for (;;) {
        int current = get();              // 1. 读当前值
        int next = current + 1;           // 2. 算新值
        if (compareAndSet(current, next)) // 3. CAS 更新
            return next;                  //    成功就返回
        // 失败 → 循环重试
    }
}
```

**这正是 volatile 做不到的那件事**——
volatile 只能保证第 1 步读到最新值，CAS 则保证了「读-算-写」整体不被打断。

---

## 问题一：ABA

### 现象

```
线程 1：读到值 A
线程 2：把值改成 B
线程 3：把值改回 A
线程 1：CAS 检查 → 值还是 A → 认为「没人改过」→ 成功
```

**值虽然还是 A，但中间已经变过好几次了。**

打个比方：你出门前看到水杯是满的，回来发现还是满的，
但其实是有人喝光了又倒满的——你以为没人动过。

### 有危害吗

看场景：

- **单纯计数**：无所谓，反正最终值对就行
- **涉及状态流转**：可能出大问题。比如余额从 100 变成 50 又变回 100，
  中间那笔扣款对依赖「值没变过」的逻辑就是不可见的

### 怎么解决

**加版本号**。每次修改都带上一个递增的版本号，CAS 时同时比较「值 + 版本号」。

Java 提供了 `AtomicStampedReference`：

```java
AtomicStampedReference<Integer> ref =
        new AtomicStampedReference<>(100, 0);   // 初始值 100，版本号 0

int stamp = ref.getStamp();
ref.compareAndSet(100, 200, stamp, stamp + 1);  // 值和版本号都对才算成功
```

这样即使值从 100 → 50 → 100，版本号也从 0 → 1 → 2，线程 1 的 CAS 会失败。

---

## 问题二：自旋开销

CAS 失败后通常要**循环重试**。如果竞争非常激烈，
大量线程会一直空转，**CPU 直接跑满，但活没干多少**。

这比阻塞还糟——阻塞的线程至少不消耗 CPU。

### 缓解手段

| 手段 | 说明 |
|---|---|
| **限制自旋次数** | JVM 的轻量级锁有自旋上限，超了就升级重量级锁阻塞掉 |
| **`LongAdder`** | 高并发计数用这个，不要死磕 `AtomicLong` |

**LongAdder 的思路很妙**：把一个变量拆成多个「cell」，
不同线程更新不同的 cell，最后求和。
**用空间换时间，把热点分散**，高并发下性能远好于 `AtomicLong`。

```java
LongAdder adder = new LongAdder();   // 高并发计数首选
adder.increment();
long sum = adder.sum();              // 求和时才汇总
```

---

## 问题三：只能保证一个变量

CAS 只能对**一个变量**做原子操作。
如果要保证 `i` 和 `j` 两个变量一起更新是原子的，CAS 无能为力。

**解决办法**：把两个变量**封装成一个对象**，用 `AtomicReference` 对整个对象做 CAS。

```java
AtomicReference<Point> ref = new AtomicReference<>(new Point(1, 2));
ref.compareAndSet(new Point(1, 2), new Point(3, 4));
```

---

## CAS vs 锁

| 对比 | CAS | synchronized |
|---|---|---|
| 策略 | 乐观（失败了重试） | 悲观（先占住不许别人进） |
| 线程会不会阻塞 | **不会**，一直跑 | 会，拿不到锁就挂起 |
| 竞争低时 | **很快**（一次 CAS 就成） | 稍慢（有锁开销） |
| 竞争高时 | **很慢**（大量空转烧 CPU） | 更好（阻塞掉不占 CPU） |
| 死锁 | 不会 | 可能 |
| 适用场景 | 简单原子操作、竞争不激烈 | 复杂临界区、竞争激烈 |

**选择标准**：
- 操作简单（就是改个值）且竞争不激烈 → CAS / Atomic 类
- 临界区复杂（多行代码、多个变量）或竞争激烈 → 老实用锁

---

## CAS 用在哪

CAS 是 JUC 整个包的地基：

| 用到 CAS 的地方 | 说明 |
|---|---|
| `AtomicInteger` / `AtomicLong` | 直接就是 CAS 自旋 |
| `LongAdder` | CAS + 分段分散热点 |
| AQS | 用 CAS 抢锁（见 [aqs.md](aqs.md)） |
| `synchronized` 轻量级锁 | 用 CAS 替换 Mark Word |
| `ConcurrentHashMap` | 用 CAS 做无锁插入 |

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| CAS 是什么 | 「值是不是 A？是就改成 B」的**原子**指令 |
| 为什么原子 | 一条 CPU 指令（cmpxchg），不可打断 |
| ABA 是什么 | 值从 A 改到 B 又改回 A，CAS 误以为没变过 |
| ABA 怎么解 | 加版本号 → `AtomicStampedReference` |
| 自旋开销怎么解 | 限制次数；高并发计数改用 `LongAdder` |
| 只能一个变量怎么办 | 封装成对象，用 `AtomicReference` |
