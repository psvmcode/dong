# volatile：一句话和两个坑

> 目标：看完能说清 volatile 干什么、不干什么，以及面试时怎么答不踩坑。

---

## 一句话

**volatile 保证「一个线程改了，其他线程立刻能看到」，并且「代码顺序不会被编译器和 CPU 乱换」。**

它不保证原子性——这是最大的坑，后面单独说。

---

## 为什么需要它：CPU 缓存的锅

现代 CPU 每个核都有自己的缓存。线程 A 在核 1 上跑，把变量读进核 1 的缓存里改；
线程 B 在核 2 上跑，读的是核 2 缓存里的**旧副本**。

```
主存        count = 0
核1 缓存    count = 1   ← 线程 A 改了，但还没写回主存
核2 缓存    count = 0   ← 线程 B 读到的还是旧值
```

打个比方：一份共享文档，每个人都拷了一份到自己的笔记本上改。
你在自己本子上改了，别人不知道，还在看自己那份旧的。

**volatile 干的事就是**：改完立刻写回主存，并且通知其他核「你那份作废了，重新去主存取」。

---

## 作用一：可见性

```java
private volatile boolean running = true;

public void stop() {
    running = false;
}

public void loop() {
    while (running) {
        // 干活
    }
}
```

**不加 volatile 会怎样**：线程 B 调了 `stop()`，但线程 A 的循环可能永远停不下来——
它一直读自己缓存里的 `true`。这个 bug 极难复现，可能压测几天才偶现一次。

**加了 volatile**：`stop()` 一改，主存立刻更新，线程 A 下一次读就看到 `false`，循环正常退出。

这是 volatile 最典型、也最正确的用法：**一个线程写，其他线程读，用来做状态开关**。

---

## 作用二：禁止指令重排序

编译器和 CPU 会为了提速 rearranging 指令顺序（只要单线程下结果不变，它们就敢换）。

单线程没问题，多线程会出事。最经典的例子是**双重检查锁单例**：

```java
private static volatile Singleton instance;

public static Singleton getInstance() {
    if (instance == null) {                    // 第一次检查
        synchronized (Singleton.class) {
            if (instance == null) {            // 第二次检查
                instance = new Singleton();    // ← 这里有问题
            }
        }
    }
    return instance;
}
```

关键在 `new Singleton()` 这一行，它实际分三步：

```
1. 分配一块内存
2. 在内存上初始化对象
3. 把 instance 指向这块内存
```

**如果允许重排序**，可能变成 `1 → 3 → 2`。
也就是说：instance 已经不是 null 了（指向了一块内存），但对象**还没初始化完**。

此时另一个线程进来，第一次检查发现 `instance != null`，直接返回——
拿到的是一个**半成品对象**，一用就崩。

**volatile 禁止了这个重排序**，保证 `instance` 赋值时对象一定已经构造完成。

> 这是面试高频题：DCL 单例为什么必须加 volatile？答「防止指令重排序导致拿到未初始化的对象」。

---

## 最大的坑：volatile 不保证原子性

这是面试官最爱追问的一刀。

```java
private volatile int count = 0;

public void add() {
    count++;   // ← 加了 volatile 也照样不安全
}
```

`count++` 看着是一行，实际是三步：

```
1. 读：把 count 读到自己的工作内存
2. 改：加 1
3. 写：写回主存
```

volatile 只能保证**第 1 步读到的是最新值**，保证不了「读-改-写」这三步不被别的线程打断。

```
线程 A：读到 0  → 改成 1 → 写回 1
线程 B：读到 0  → 改成 1 → 写回 1     ← 两个线程都加了，结果却是 1
```

**结论**：volatile 管「看得见」，管不了「同时改」。
要保证原子性，用 `synchronized` 或 `AtomicInteger`。

| 场景 | volatile 够吗 | 该用什么 |
|---|---|---|
| 一个线程写，其他线程读（状态开关） | **够** | volatile |
| 多个线程都要写（计数器） | **不够** | AtomicInteger / synchronized |
| 需要「读-改-写」整体不被打断 | **不够** | synchronized / Lock |

---

## 底层是怎么做到的

不用背，知道大致原理就行：

**写 volatile 变量时**，JVM 会在写操作后面加一条「store barrier」，
强制把工作内存的值刷到主存；**读之前**加「load barrier」，
强制从主存重新读，并让自己缓存里的旧副本失效。

硬件层面靠的是**缓存一致性协议**（x86 上是 MESI）：
某个核改了数据，会通过总线通知其他核「这份数据我改过了，你们的副本作废」。

还有一层是 **happens-before 规则**：
对一个 volatile 变量的写，happens-before 后续对这个变量的读。
这是 Java 内存模型给开发者的**承诺**——你按规则写，JVM 保证结果可预期。

---

## 记忆口诀

| 特性 | volatile | synchronized |
|---|---|---|
| 可见性 | ✅ | ✅ |
| 禁止重排序 | ✅ | ✅ |
| 原子性 | ❌ | ✅ |
| 会不会阻塞 | ❌ 不会 | ✅ 会 |

一句话记：**volatile 是轻量版的可见性保证，管不了原子性，也不会让线程阻塞。**

---

## 什么时候用

**该用**：

1. **状态开关**——一个线程改标志位，其他线程轮询它决定是否退出（前面的 `running` 例子）
2. **双重检查锁单例**——防止重排序拿到半成品对象
3. **一次性发布**——配置加载完打个 `volatile boolean initialized = true`，其他线程看到 true 时配置一定已经就绪

**不该用**：

1. 计数器、累加器——用 `AtomicInteger`
2. 依赖当前值的判断再修改（`if (x == 0) x = 1;`）——用 `synchronized`
3. 任何需要「多步操作整体不被打断」的场景

**判断标准**：问自己一句——**这个变量的写操作，是否依赖它当前的值？**
不依赖（比如直接赋 `true`/`false`），volatile 就够；依赖（`count++`、`if` 后再改），就不够。
