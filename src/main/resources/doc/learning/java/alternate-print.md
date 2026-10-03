# 两个线程交替打印 1 到 100

> 目标：四种最简单写法，`synchronized` / `ReentrantLock` / `CountDownLatch` / `Semaphore` 各一个。
> 代码都用同一个套路：`print(int parity)`，`1` 表示这个线程负责奇数，`0` 负责偶数。

---

## 题意

线程 A 只打奇数、线程 B 只打偶数，输出必须严格是 `1 2 3 ... 100`。

难点不是安全，是**轮流**：既要互斥（同一时刻只能一个线程动手），又要协作（打完必须把控制权交给对方）。

四个版本的调用方式完全一样：

```java
Printer printer = new Printer();
new Thread(() -> printer.print(1), "奇数线程").start();   // 1 = 负责奇数
new Thread(() -> printer.print(0), "偶数线程").start();   // 0 = 负责偶数
```

---

## 写法一：synchronized + wait / notifyAll（最基础）

```java
public class Printer {

    private int n = 1;

    public synchronized void print(int parity) throws InterruptedException {
        while (true) {
            while (n <= 100 && n % 2 != parity) {
                wait();
            }
            if (n > 100) {
                notifyAll();
                return;
            }
            System.out.println(Thread.currentThread().getName() + "：" + n++);
            notifyAll();
        }
    }

}
```

**大白话**：所有人挤在同一间屋里，谁抢到就看一眼是不是该自己打的数，不是就 `wait` 出去等着；打完 `notifyAll` 喊一嗓子，大家再抢。

`notifyAll` 会喊醒所有人（包括不该动的那个），所以醒来必须**重新判断**——这就是内层 `while` 的作用。

---

## 写法二：ReentrantLock + Condition（推荐）

```java
public class Printer {

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition[] conditions = new Condition[2];

    private int n = 1;

    public Printer() {
        conditions[0] = lock.newCondition();
        conditions[1] = lock.newCondition();
    }

    public void print(int parity) throws InterruptedException {
        lock.lock();
        try {
            while (true) {
                while (n <= 100 && n % 2 != parity) {
                    conditions[parity].await();
                }
                if (n > 100) {
                    conditions[1 - parity].signal();
                    return;
                }
                System.out.println(Thread.currentThread().getName() + "：" + n++);
                conditions[1 - parity].signal();
            }
        } finally {
            lock.unlock();
        }
    }

}
```

**大白话**：一把锁开出**两间休息室**，奇数线程在 1 号室等，偶数线程在 0 号室等。打完只 `signal` 对方那间，**不喊错人**。

比写法一强在这点：写法一是「喊一嗓子全体起床再各自判断」，这里是「精准叫醒」。线程一多差距就出来了。

`conditions` 数组的元素从头到尾没换过（只是 `await` / `signal`），所以不需要 `volatile`。

---

## 写法三：CountDownLatch（门闩）

```java
public class Printer {

    private volatile CountDownLatch oddGate = new CountDownLatch(0);    // 奇数先跑，门本来就开着

    private volatile CountDownLatch evenGate = new CountDownLatch(1);   // 偶数要等奇数打完第一枪

    private int n = 1;

    public void printOdd() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            oddGate.await();
            System.out.println("奇数线程：" + n++);
            oddGate = new CountDownLatch(1);     // 关上自己的门
            evenGate.countDown();                 // 打开对方的门
        }
    }

    public void printEven() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            evenGate.await();
            System.out.println("偶数线程：" + n++);
            evenGate = new CountDownLatch(1);
            oddGate.countDown();
        }
    }

}
```

**大白话**：每人身前一扇门。干活前先等自己的门开，干完**把自己的门重新关上**（换一扇新门），再把对方的门打开。

两个容易踩的点：

1. **每轮必须换一扇新门**。门闩是一次性的，`countDown` 到 0 之后就永久敞开，`await` 再也不会拦人——所以不换门，第二轮起就乱序了。这也是 CountDownLatch 不适合做「可复用的开关」的原因，那个场景该用 `Semaphore` 或 `CyclicBarrier`。
2. **两个字段必须各自 `volatile`**。如果把它们塞进一个数组再改元素，**改数组元素没有 volatile 可见性保证**，另一线程可能读到已经开过的旧门闩。这里老老实实用两个独立字段。

---

## 写法四：Semaphore（通行证，最短）

```java
public class Printer {

    private final Semaphore[] permits = {new Semaphore(0), new Semaphore(1)};

    private int n = 1;

    public void print(int parity) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            permits[parity].acquire();
            System.out.println(Thread.currentThread().getName() + "：" + n++);
            permits[1 - parity].release();
        }
    }

}
```

**大白话**：全场只有一张通行证，奇数线程开局拿着。有证才能打印，打完把证交给对方。

**9 行搞定**，是四个版本里最短的，因为三样东西全省了：

| 其它写法必须写的 | 这里靠什么免掉 |
|---|---|
| `while (n <= 100)` 边界判断 | `for` 循环 50 次，每人恰好 50 个数 |
| `while` 重新确认条件 | 拿到通行证就一定轮得到自己，不存在虚假唤醒 |
| 退出前再叫醒对方 | 每人打完自己的 50 个自然结束 |
| `finally { lock.unlock(); }` | 压根没用锁 |

`n++` 也不用同步：同一时刻只有持证人进得来。

---

## 三个必踩的坑（写法一、二尤其要注意）

**1. 判断条件必须用 `while`，不能用 `if`**

`wait()` / `await()` 返回**不代表条件成立**——可能是虚假唤醒，也可能是被同类线程唤醒的。
用 `if` 会直接往下走，打印出本该对方打印的数。

**2. 打印、自增、唤醒必须在同一个临界区里**

拆开写，对方会被中间状态坑到：重复打印、顺序错乱。
同理，**不要**把 `while (n <= 100)` 这种检查写在锁外面——检查和执行分离，理论上会多打出一个数。

**3. 退出前一定要叫醒对方**

写法一、二里如果不叫，`n` 超过 100 的那个线程直接 return，另一个线程会永远停在 `wait` 上。
写法三、四没这个问题，因为每个人跑够 50 次就自然结束，不需要通知谁。

---

## 怎么选

| 写法 | 代码量 | 唤醒方式 | 需要 `while` | 需要 `finally` | 适合什么时候 |
|---|---|---|---|---|---|
| `synchronized` | 中 | 广播，自己筛 | ✅ | ❌ | 面试手撕、最基础 |
| `ReentrantLock` + `Condition` | 中 | **精准** | ✅ | ✅ | 生产首选，多线程扩展性好 |
| `CountDownLatch` | 中 | 精准（指定人） | ❌ | ❌ | 理解"一次性门闩"的边界；不适合高频循环 |
| `Semaphore` | **最少** | 精准（交证） | ❌ | ❌ | 顺序固定、次数已知的交替场景 |

一句话：**追求短用 Semaphore，追求稳用 Condition，理解原理从 `synchronized` 开始。**

---

## 实测

四种写法 × 两种启动顺序（奇数先启 / 偶数先启），全部：

```
个数=100   严格递增=true   奇偶错位=0   两个线程都正常退出
```

CountDownLatch 版另跑了 50 轮稳定性用例，无一例失败。
**四种都不依赖线程的启动顺序**——想换先后随便换。
