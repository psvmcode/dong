# ThreadLocal：为什么在线程池里会内存泄漏

> 目标：说清它的实现、泄漏的真实原因，以及正确用法。

---

## 一句话

**ThreadLocal 给每个线程一份独立的变量副本，线程之间互不干扰。**

它不是用来「共享数据」的，恰恰相反，是用来**隔离**数据的。

典型用途：在一次请求的处理链路里传递用户信息，
避免每个方法都多传一个 `userId` 参数。

---

## 怎么用

```java
private static final ThreadLocal<User> currentUser = new ThreadLocal<>();

// 在拦截器中设置
currentUser.set(user);

// 在业务代码任意位置读取
User user = currentUser.get();

// 用完后必须清理
currentUser.remove();
```

同一份代码，线程 A 存的用户和线程 B 存的用户互不影响。

---

## 实现原理

**很多人以为数据是存在 ThreadLocal 对象里的，其实反过来——数据存在线程自己身上。**

每个 `Thread` 对象内部有一个 `threadLocals` 字段，类型是 `ThreadLocalMap`：

```
Thread
  └── threadLocals (ThreadLocalMap)
        └── Entry[] table
              ├── Entry(key=ThreadLocal对象(弱引用), value=你存的值)
              └── ...
```

所以：

| 操作 | 实际做了什么 |
|---|---|
| `set(value)` | 拿到**当前线程**的 map，以 `this`（ThreadLocal 对象）为 key 存进去 |
| `get()` | 拿到当前线程的 map，以 `this` 为 key 取出来 |
| `remove()` | 从当前线程的 map 里删掉这个 entry |

**关键结论**：ThreadLocal 本身不存数据，它只是个**查询用的 key**。

这也就解释了为什么 ThreadLocal 通常声明为 `static final`——
它只是个 key，全局一个就够，没必要每个实例一个。

---

## 内存泄漏的真正原因

这是面试高频题，但很多人答得不准确。

### 先说弱引用

`ThreadLocalMap` 里的 **key 是弱引用**，value 是强引用：

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    Object value;   // 强引用
}
```

**为什么 key 用弱引用**：如果 ThreadLocal 对象（比如 `currentUser` 这个静态变量）被置为 null，
弱引用不会阻止它被 GC 回收。

### 泄漏怎么发生的

```
1. ThreadLocal 对象被回收（弱引用 → key 变成 null）
2. 但 value 还是强引用，且被 Thread 的 threadLocals 持有
3. 如果线程一直活着（比如线程池里的线程），这条引用链就一直在：
   Thread → threadLocals → Entry → value
4. value 永远无法被访问（key 是 null 了），但也无法被回收 → 泄漏
```

**关键前提：线程长期存活。**

所以准确的说法是：

> **ThreadLocal 内存泄漏的根源是「线程长期存活 + value 强引用无法回收」，
> 弱引用只是让 key 变成 null，加剧了这个问题，但不是根本原因。**

如果是在**线程池**里（线程会复用、长期存活），风险最大。
如果是自己 new 的普通线程，用完就销毁了，反而问题不大。

### 那为什么还要用弱引用

因为如果 key 是强引用，那 ThreadLocal 对象本身也永远无法回收，泄漏更严重。
弱引用至少给了 key 被回收的机会（虽然 value 还得靠 remove）。

---

## 怎么避免

**用完必须 `remove()`**——这是唯一可靠的办法。

```java
try {
    currentUser.set(user);
    // ... 业务逻辑
} finally {
    currentUser.remove();   // 必须放在 finally 里
}
```

**为什么一定要在 finally**：业务代码抛异常时，
如果 remove 写在正常流程末尾，就跳过了，照样泄漏。

在 Web 应用里，通常在**拦截器的 afterCompletion** 里统一清理。

> JDK 本身在 `ThreadLocalMap` 的 `set`/`get` 时也会顺带清理 key 为 null 的 entry，
> 但**这是被动的、不及时的**，不能依赖它。

---

## 线程池里的另一个坑：数据串了

这个比内存泄漏更容易踩，而且后果更直接。

**线程是复用的**。上一个任务往 ThreadLocal 里塞了用户 A 的信息没清理，
下一个任务复用了这个线程，直接 `get()` 就拿到了用户 A 的数据。

```
任务1（用户A）：set(A) → 业务处理 → 【没 remove】
任务2（用户B）：get() → 拿到 A ！！！  ← 数据串了
```

这会导致**越权看到别人的数据**——在资金、订单类系统里是严重事故。

**同一个原因（没 remove），两种后果**：
1. 内存泄漏（长期看）
2. 数据串了（立刻炸）

所以 `remove()` 不是可选项，是**必须项**。

---

## 子线程能不能拿到父线程的值

默认情况下**不能**。ThreadLocal 是线程隔离的，子线程当然是另一个线程。

如果确实需要传递，用 `InheritableThreadLocal`：

```java
private static final InheritableThreadLocal<String> ctx = new InheritableThreadLocal<>();
```

它在**创建子线程时**把父线程的值复制过去。

**但线程池场景下这个也不灵**：
线程池里的线程早就创建好了，不会在你提交任务时重新复制。
而且线程复用会让值变得混乱。

线程池里要传上下文，用阿里开源的 `TransmittableThreadLocal`（TTL），
它在**任务提交时**捕获、在**任务执行时**回放。

---

## 常见误区

**误区一：ThreadLocal 是用来解决共享变量线程安全问题的**
不是。它不共享，是给每个线程一份副本。
如果要共享还得同步，那用 `synchronized` 或原子类。

**误区二：用了 ThreadLocal 就不用加锁了**
对，因为根本没共享。但前提是它确实是 per-thread 的语义。

**误区三：用完不用管，反正线程结束会回收**
**在线程池里线程不会结束**。这是最容易出事的地方。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 数据存在哪 | **线程自己**的 ThreadLocalMap 里，ThreadLocal 只是 key |
| key 是什么引用 | **弱引用**；value 是强引用 |
| 泄漏的根源 | 线程长期存活 + value 强引用无法回收（弱引用不是根因） |
| 怎么避免 | 用完 `remove()`，**必须放 finally** |
| 线程池里的另一个坑 | 线程复用导致**数据串了**（可能越权） |
| 父子线程传递 | `InheritableThreadLocal`（线程池里要用 TTL） |
