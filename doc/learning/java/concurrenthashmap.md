# ConcurrentHashMap：从分段锁到 CAS 的演进

> 目标：说清 JDK 1.7 和 1.8 的实现差异，以及为什么 1.8 的读操作几乎不加锁。

---

## 一句话

**ConcurrentHashMap 是线程安全的 HashMap，1.7 用分段锁，1.8 改用 CAS + synchronized 锁单个桶。**

演进的核心思路：**把锁的粒度不断做小**，从锁整张表 → 锁一段 → 锁一个桶 → 能不加锁就不加。

---

## 对比：三种 Map

| 实现 | 线程安全吗 | 特点 |
|---|---|---|
| `HashMap` | ❌ | 多线程 put 可能导致死循环（1.7 头插法扩容时）或数据丢失 |
| `Hashtable` | ✅ | 所有方法加了 `synchronized`，**锁整张表**，性能极差 |
| `ConcurrentHashMap` | ✅ | 锁粒度小，高并发下性能好 |

**`Hashtable` 基本可以放弃了**——想要线程安全就用 ConcurrentHashMap。

还有个 `Collections.synchronizedMap(new HashMap<>())`，
本质和 Hashtable 一样是锁整个对象，性能同样差。

---

## JDK 1.7：分段锁（Segment）

**思路**：把整个哈希表**分成 16 段**（Segment），每段是一把独立的锁。

```
ConcurrentHashMap
  ├── Segment[0]  (锁[0])  → HashEntry[]
  ├── Segment[1]  (锁[1])  → HashEntry[]
  ├── ...
  └── Segment[15] (锁[15]) → HashEntry[]
```

不同 Segment 上的操作**互不干扰**，可以并发。

**效果**：理论上最多支持 16 个线程同时写（各写各的段）。

**缺点**：
- Segment 数量初始化后就固定了，不好扩容
- 锁的粒度还是偏粗——一个 Segment 里可能有多个桶，它们之间其实不用互斥
- 结构复杂，代码难维护

---

## JDK 1.8：CAS + 锁单个桶

1.8 做了大改，**废弃了 Segment**，回归到和 HashMap 类似的「数组 + 链表 + 红黑树」结构，
用更细粒度的机制保证并发安全。

### 结构

```
table[] （Node 数组）
  ├── [0] → null
  ├── [1] → Node → Node → TreeNode(红黑树)
  ├── [2] → Node
  └── ...
```

和 HashMap 一样：链表长度超过 8 且数组长度 ≥ 64 时，链表转成红黑树。

### put 流程（重点）

```
1. 计算 hash，定位到数组下标 i

2. 如果 table[i] 是空的:
   → 用 CAS 直接写入，不用加锁            ← 无竞争时零开销

3. 如果 table[i] 不是空的（可能有冲突）:
   → 用 synchronized 锁住这个桶的头节点
   → 在链表/红黑树里插入或更新
   → 释放锁

4. 如果 table[i] 的 hash 是 MOVED(-1):
   → 说明正在扩容，当前线程帮忙一起迁移

5. 插入后检查链表长度，≥8 则转红黑树
```

**关键改进**：

| 1.7 | 1.8 |
|---|---|
| 锁一个 Segment（含多个桶） | **只锁当前这一个桶** |
| 锁的粒度粗 | 粒度细化到**单个数组元素** |
| 无竞争也要走加锁流程 | **无竞争时用 CAS，完全不阻塞** |

只在真正发生 hash 冲突时（要往同一个桶里写）才加锁，
冲突本来就少，所以绝大多数 put 操作**根本不会加锁**。

而且用的是 `synchronized` 而不是 `ReentrantLock`——
因为 1.6 之后 synchronized 有了锁升级优化，在低竞争下非常快，
还省内存（不用每个节点都创建 Lock 对象）。

---

## 读操作为什么几乎不加锁

**这是 ConcurrentHashMap 最精妙的地方。**

`get()` 操作**全程无锁**（除了正好读到正在迁移的桶）。

靠三件事保证：

### 1. `volatile` 保证可见性

`table` 数组本身是 volatile 的，Node 的 `value` 和 `next` 也是 volatile 的。

所以一个线程 put 进去的新节点，另一个线程 get 时**立刻可见**。
（呼应 [volatile.md](volatile.md)）

### 2. 数组读取的天然安全

数组元素用 `Unsafe.getObjectVolatile()` 读，保证读到最新值。

### 3. 节点不可变倾向

Node 的 `value` 和 `next` 是 volatile，且**节点一旦插入，next 只会往后加**，
不会在中间插入——所以读的时候顺着 next 走，不会读到断链。

**结果**：get 操作和普通 HashMap 一样快，实现了**读操作的完全并发**。

---

## 扩容机制

1.8 的扩容有个巧妙设计：**多线程协同迁移**。

```
正在扩容时，其他线程来 put/get:
→ 发现当前桶的 hash 是 MOVED(-1)
→ 不阻塞等待，而是【帮忙一起迁移数据】
→ 迁移完再继续自己的操作
```

每个线程负责一段区间的迁移（默认每次领 16 个桶），
**把扩容的工作量分摊到所有访问线程上**，加快扩容速度。

对比 JDK 1.7：扩容是单个 Segment 内部的事，其他线程只能等着。

---

## size() 是怎么算的

这是个容易被追问的细节。

**问题**：统计元素个数要遍历所有桶，遍历过程中可能有并发修改，怎么保证准确？

**1.8 的做法**：
- 维护一个 `baseCount`，用 CAS 更新
- 竞争激烈时，用 `CounterCell[]` 数组分散计数（和 `LongAdder` 一样的思路）

`size()` 返回的是 **baseCount + 所有 CounterCell 的和**。

**注意**：这个值在并发场景下是**估计值**，不是精确值——
因为统计过程中可能还有线程在改。

如果你需要在遍历时看到完全一致的数据，那就得加锁了，
但这违背了 ConcurrentHashMap 高并发的初衷。

要强一致，用 `mappingCount()`（返回 long）或干脆外部加锁。

---

## 常见误区

**误区一：ConcurrentHashMap 能保证复合操作的原子性**

不能。它只保证**单个操作**（`put`、`get`）是线程安全的。

```java
// 这不是原子的！典型的 check-then-act
if (!map.containsKey(key)) {
    map.put(key, value);     // 两步之间可能被人插队
}
```

要用 `putIfAbsent(key, value)` 或 `computeIfAbsent(key, k -> value)`。

**误区二：可以在遍历时随便增删**

ConcurrentHashMap 的迭代器是**弱一致性**的——
遍历时如果别线程改了，可能看到也可能看不到新数据，但**不会抛 ConcurrentModificationException**。

这和 HashMap 的 fail-fast 不同，是刻意为之（为了并发性能）。

**误区三：ConcurrentHashMap 的 key 或 value 可以为 null**

**都不行**。HashMap 允许 null，ConcurrentHashMap **不允许**。

原因是**二义性**：

```java
map.get(key) == null
```

这个 null 是「key 不存在」，还是「key 存在但 value 是 null」？
在并发场景下无法区分（`containsKey` 和 `get` 之间可能被别人改了）。
所以干脆禁止 null，避免歧义。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 1.7 实现 | 分段锁 Segment，最多 16 个线程并发写 |
| 1.8 实现 | 数组+链表+红黑树，**CAS + synchronized 锁单个桶** |
| 为什么换掉 Segment | 粒度还是粗；1.8 只在 hash 冲突时才加锁，无冲突用 CAS |
| 读为什么不加锁 | table 和 Node 的 value/next 都是 **volatile** |
| 扩容有什么特别 | 其他线程**帮忙迁移**，不干等 |
| size() 准吗 | 并发下是**估计值**（baseCount + CounterCell[]） |
| null 允许吗 | **key 和 value 都不允许**（避免 get 返回 null 的二义性） |
| 复合操作安全吗 | 不安全，要用 `putIfAbsent` / `computeIfAbsent` |
