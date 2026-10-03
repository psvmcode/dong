# AQS：JUC 里大半组件的共同骨架

> 目标：说清一个 `state` + 一个队列怎么撑起 ReentrantLock、CountDownLatch、Semaphore。

---

## 一句话

**AQS 的核心就两件事：一个 `volatile int state` 表示资源状态，一个 FIFO 队列存放等待的线程。**

抢到资源的线程去干活，没抢到的进队列排队，前驱释放后唤醒后继。
就这么简单，但它是 `ReentrantLock`、`CountDownLatch`、`Semaphore`、`ReentrantReadWriteLock` 的共同基础。

---

## 为什么要有它

在 AQS 之前，每个同步组件（锁、信号量、倒计时门闩）都要自己实现：
排队、阻塞、唤醒、状态管理……重复且容易错。

AQS 把这些**公共部分抽出来**，只留两个方法给子类实现：

| 方法 | 含义 |
|---|---|
| `tryAcquire(int)` | 尝试获取资源，成功返回 true |
| `tryRelease(int)` | 尝试释放资源 |

**这就是模板方法模式**：骨架固定，具体「什么叫获取到」由子类定义。

---

## 两个核心成员

### 1. state（同步状态）

```java
private volatile int state;
```

`volatile` 保证可见性，用 CAS 保证原子修改。

**state 的含义由子类定义**——这是 AQS 灵活的关键：

| 组件 | state 表示什么 |
|---|---|
| `ReentrantLock` | 0 表示没锁；>0 表示被持有，数值是**重入次数** |
| `Semaphore` | 剩余**许可证数量** |
| `CountDownLatch` | 还需要倒数的**计数值** |
| `ReentrantReadWriteLock` | 高 16 位读锁数量，低 16 位写锁重入次数 |

**同一套机制，靠 state 的不同含义，实现了完全不同的同步语义。**

### 2. CLH 队列（等待队列）

没抢到资源的线程会被包装成一个 **Node 节点**，加入一个**双向 FIFO 队列**。

```
head → [Node1] ⇄ [Node2] ⇄ [Node3] ← tail
        (已拿到锁)   (等待中)   (等待中)
```

队列里的线程会**挂起**（`LockSupport.park()`），不消耗 CPU。
前驱节点释放资源时**唤醒**后继（`LockSupport.unpark()`）。

**为什么用双向**：因为节点可能需要取消（超时、中断），
取消时要快速找到前驱把链接改掉，单向链表做不到。

---

## 获取资源的流程

```
1. tryAcquire(尝试拿)
   ├─ 成功 → 直接返回，去干活
   └─ 失败 ↓

2. addWaiter(把自己包装成 Node 加到队尾)

3. acquireQueued(在队列里循环):
   for (;;) {
       if (前驱是 head && tryAcquire 成功) {
           把自己设为 head，返回;      // 拿到锁了
       } else {
           LockSupport.park();      // 挂起，等别人唤醒
       }
   }
```

**几个设计细节**：

- **只有前驱是 head 的结点才去尝试获取**——保证 FIFO 公平，不插队
- **挂起前会再试几次**——因为可能前驱马上就要释放了，能避免一次挂起/唤醒的开销
- **被唤醒后重新循环**——不是唤醒就拿到锁了，还要再 tryAcquire 竞争一次

---

## 释放资源的流程

```
1. tryRelease(释放 state)

2. 如果 state 变成「可用」状态:
   找到 head 的后继节点，LockSupport.unpark() 唤醒它
```

唤醒之后，被唤醒的线程在 `acquireQueued` 的循环里重新尝试获取。

---

## 公平与非公平

| 模式 | 行为 | 特点 |
|---|---|---|
| **非公平**（默认） | 新来的线程**先插队试一次** CAS，抢不到再排队 | 吞吐高，但可能造成队列里的线程**饿死** |
| **公平** | 新来的线程先看队列有没有人，**有就直接排队** | 公平，但吞吐低（每次都要检查队列） |

```java
new ReentrantLock();        // 默认非公平
new ReentrantLock(true);    // 公平锁
```

**为什么默认非公平？**

因为**唤醒一个挂起的线程开销很大**（涉及内核态切换）。
如果此刻刚好有个新线程来了，直接让它跑，比唤醒队列里的线程更划算——
**总体吞吐量更高**。

代价是队列里等了很久的线程可能一直抢不到，但实践中这种情况很少。

---

## 共享模式 vs 独占模式

| 模式 | 含义 | 典型组件 |
|---|---|---|
| **独占**（exclusive） | 同一时刻只有一个线程能获取 | `ReentrantLock` |
| **共享**（shared） | 多个线程可以同时获取 | `Semaphore`、`CountDownLatch`、`ReadWriteLock` 的读锁 |

对应两套方法：`tryAcquire/tryRelease` 和 `tryAcquireShared/tryReleaseShared`。

共享模式下，一个节点被唤醒后，如果还有剩余资源，会**继续唤醒后面的节点**（传播）。

---

## 为什么它是面试重点

因为 AQS 是**理解 JUC 的钥匙**。理解了它，下面这些就都是同一个套路：

- `ReentrantLock` = state 表示重入次数 + 独占模式
- `Semaphore` = state 表示许可证数 + 共享模式
- `CountDownLatch` = state 表示计数值，`await` 就是「获取」，`countDown` 就是「释放」
- `ReentrantReadWriteLock` = state 高低位拆分 + 读写两种模式

**面试时这样答会加分**：不要背源码，说清「一个 state + 一个队列 + 模板方法」这个设计思想。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| AQS 是什么 | JUC 同步组件的**骨架**（模板方法模式） |
| 核心成员 | `volatile int state` + FIFO 双向等待队列 |
| state 谁定义 | 子类（锁=重入次数，信号量=许可证数，门闩=计数值） |
| 拿不到锁怎么办 | 包装成 Node 进队列，`LockSupport.park()` 挂起 |
| 怎么唤醒 | 前驱释放时 `unpark` 后继 |
| 为什么默认非公平 | 唤醒挂起线程开销大，让新线程插队吞吐更高 |
| 子类要实现什么 | `tryAcquire` / `tryRelease` |
