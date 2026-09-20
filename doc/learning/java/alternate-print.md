# 两个线程严格交叉打印 1 到 100

> 目标：看完能一口气写出几种正确写法，并说清几个必踩的坑——
> 为什么判断条件必须用 `while`、为什么唤醒别人之后自己必须等、为什么打印和自增必须绑在一起。
> 最后给了一个只用一个方法就搞定的最短版本。

---

## 题目与真正的难点

线程 A 只打奇数、线程 B 只打偶数，输出必须严格是 `1 2 3 4 ... 100`，一个数不多、一个数不少、顺序不乱。

难点**不是并发安全**，而是**交替**：

- 同一时刻只允许一个线程干活（互斥）
- 干完必须把控制权**交给对方**，不能自己接着干（协作）

只有互斥没有协作，结果就是某个线程一路打印到底——这是最常见的错误答案。

---

## 三种典型错法

### 错法一：只加锁，不等待

```java
public synchronized void print() {
    System.out.println(current);   // 锁住了，但没交替
    current++;
}
```

锁只保证不打架，不保证轮流。哪个线程抢到锁哪个打印，可能 A 一口气打完 100 个。

### 错法二：判断条件用 `if`

```java
if (current % 2 == 0) {
    wait();          // ← 用 if，被唤醒后不重新判断
}
System.out.println(current);
```

`wait()` 返回**不代表条件成立**。可能是一次虚假唤醒，也可能是被同类线程唤醒的。
用 `if` 会直接往下走，打印出本该对方打印的数字。**判断条件一律用 `while`。**

### 错法三：打印和自增分开

```java
System.out.println(current);   // 打印 5
notifyAll();                   // 叫醒对方，但 current 还是 5
current++;                     // 慢了一步
```

对方被唤醒后拿到的还是旧值，要么重复打印、要么顺序错乱。
**打印、自增、唤醒必须放在同一个临界区里，一气呵成。**

---

## 解法一：synchronized + wait / notifyAll（最经典，必须会手写）

```java
public class OddEvenPrinter {

    private static final int MAX = 100;

    /**
     * 当前待打印的数字。
     */
    private int current = 1;

    /**
     * 打印奇数。
     */
    public synchronized void printOdd() throws InterruptedException {
        while (current <= MAX) {
            while (current % 2 == 0) {
                wait();
            }
            if (current > MAX) {
                break;
            }
            System.out.println("odd  -> " + current);
            current++;
            notifyAll();
        }
        // 退出前再叫一次：否则对方可能永远停在 wait 上
        notifyAll();
    }

    /**
     * 打印偶数。
     */
    public synchronized void printEven() throws InterruptedException {
        while (current <= MAX) {
            while (current % 2 == 1) {
                wait();
            }
            if (current > MAX) {
                break;
            }
            System.out.println("even -> " + current);
            current++;
            notifyAll();
        }
        notifyAll();
    }

}
```

**四个关键点**：

| 点 | 说明 |
|---|---|
| 条件用 `while` 不用 `if` | 防虚假唤醒，被叫醒后重新确认轮到自己没有 |
| 唤醒用 `notifyAll` | 两个线程还好，条件一复杂，`notify` 可能叫醒同类线程导致谁都不动 |
| 打印 + 自增 + 唤醒在一起 | 三者不能拆开，否则中间状态被对方看见 |
| 退出循环后再唤醒一次 | 否则先结束的那个线程会把对方永久留在 `wait` 上 |

**为什么用 `notifyAll` 而不是 `notify`**：`notify` 只随机叫醒一个。
这里恰好只有两个线程且条件互斥，用 `notify` 也能跑通；但条件一旦变复杂（比如三个线程轮流打印），
`notify` 可能叫醒的还是不满足条件的那个，没人再唤醒真正该醒的线程 —— 直接死锁。养成用 `notifyAll` 的习惯。

---

## 解法二：ReentrantLock + Condition（精准唤醒，生产推荐）

`wait/notifyAll` 是一把锁一个等待队列，唤醒是「广撒网」。
`Condition` 可以从一把锁上开出多个队列，**只叫醒该叫醒的那一个**。

```java
public class OddEvenPrinterByCondition {

    private static final int MAX = 100;

    /**
     * 独占锁。
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 奇数线程的等待队列。
     */
    private final Condition oddCondition = lock.newCondition();

    /**
     * 偶数线程的等待队列。
     */
    private final Condition evenCondition = lock.newCondition();

    /**
     * 当前待打印的数字。
     */
    private int current = 1;

    /**
     * 打印奇数。
     */
    public void printOdd() throws InterruptedException {
        lock.lock();
        try {
            while (current <= MAX) {
                while (current % 2 == 0) {
                    oddCondition.await();
                }
                if (current > MAX) {
                    break;
                }
                System.out.println("odd  -> " + current);
                current++;
                evenCondition.signal();
            }
            evenCondition.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 打印偶数。
     */
    public void printEven() throws InterruptedException {
        lock.lock();
        try {
            while (current <= MAX) {
                while (current % 2 == 1) {
                    evenCondition.await();
                }
                if (current > MAX) {
                    break;
                }
                System.out.println("even -> " + current);
                current++;
                oddCondition.signal();
            }
            oddCondition.signal();
        } finally {
            lock.unlock();
        }
    }

}
```

与解法一的差别只有两点：`await/signal` 替代 `wait/notifyAll`，`unlock` 必须放 `finally`。
**注意 `await()` 会释放锁**，被唤醒后重新抢锁，所以醒来第一件事还是重新判断条件。

---

## 解法三：Semaphore（最直观的「通行证」思路）

把「轮到谁」建模成一张通行证：奇数线程开局持有一张，打印完把通行证交给偶数线程。

```java
public class OddEvenPrinterBySemaphore {

    private static final int MAX = 100;

    /**
     * 奇数线程的通行证，初始 1 张：让奇数先走。
     */
    private final Semaphore oddPermit = new Semaphore(1);

    /**
     * 偶数线程的通行证，初始 0 张：先等着。
     */
    private final Semaphore evenPermit = new Semaphore(0);

    /**
     * 当前待打印的数字。
     */
    private int current = 1;

    /**
     * 打印奇数。
     */
    public void printOdd() throws InterruptedException {
        for (int i = 1; i <= MAX; i += 2) {
            oddPermit.acquire();
            System.out.println("odd  -> " + current);
            current++;
            evenPermit.release();
        }
    }

    /**
     * 打印偶数。
     */
    public void printEven() throws InterruptedException {
        for (int i = 2; i <= MAX; i += 2) {
            evenPermit.acquire();
            System.out.println("even -> " + current);
            current++;
            oddPermit.release();
        }
    }

}
```

**为什么这段不需要 `while` 判断**：顺序是被信号量**硬性规定**的，
不是靠「检查条件」碰运气——`acquire()` 拿不到就阻塞，拿到就一定轮到自己，所以不存在虚假唤醒的问题。

`current++` 也不需要额外同步：两个线程不可能同时持有通行证。
这也说明一件事——**信号量同时提供了互斥和顺序**，代码比 `wait/notify` 干净。

---

## 解法四：LockSupport（最底层，park / unpark）

`park/unpark` 直接操作线程，不需要锁对象。特点是可以**先发许可后等待**：
哪怕 `unpark` 发生在 `park` 之前，线程也不会丢这次唤醒（这是它比 `wait/notify` 安全的地方）。

```java
public class OddEvenPrinterByPark {

    private static final int MAX = 100;

    /**
     * 当前待打印的数字，两个线程都要读，必须可见。
     */
    private volatile int current = 1;

    /**
     * 按奇偶打印，打完叫醒对方。
     *
     * @param name    线程名
     * @param parity  该线程负责的奇偶，奇数传 1，偶数传 0
     * @param partner 对方线程
     */
    public void print(String name, int parity, Thread partner) {
        while (current <= MAX) {
            while (current <= MAX && current % 2 != parity) {
                LockSupport.park();
            }
            if (current > MAX) {
                break;
            }
            System.out.println(name + " -> " + current);
            current++;
            LockSupport.unpark(partner);
        }
        LockSupport.unpark(partner);
    }

}
```

```java
OddEvenPrinterByPark printer = new OddEvenPrinterByPark();
Thread[] threads = new Thread[2];
threads[0] = new Thread(() -> printer.print("odd ", 1, threads[1]));
threads[1] = new Thread(() -> printer.print("even", 0, threads[0]));
threads[0].start();
threads[1].start();
```

`Thread.start()` 有 happens-before 保证，所以两个线程读到的 `threads[1]` / `threads[0]` 一定已经赋值。

**`park` 也会无缘无故返回**，条件判断照样得用 `while`——这一点和 `wait` 一样，别因为「底层」就放松警惕。

---

## 最短写法：一个方法同时打印奇数和偶数

前面四种解法都要写 `printOdd` 和 `printEven` 两份几乎一模一样的代码。
能不能只有一份？可以——**把「我是奇数还是偶数」变成入参**。

用 Semaphore 写出来只要 9 行：

```java
public class AlternatePrinter {

    /**
     * 通行证数组，下标 0 给偶数线程，下标 1 给奇数线程。
     * 奇数线程开局就持有一张，所以第一个打印出来的一定是 1。
     */
    private final Semaphore[] permits = {new Semaphore(0), new Semaphore(1)};

    /**
     * 当前待打印的数字。
     */
    private int current = 1;

    /**
     * 打印属于自己的那 50 个数。
     *
     * @param parity 奇数线程传 1，偶数线程传 0
     */
    public void print(int parity) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            permits[parity].acquire();
            System.out.println(Thread.currentThread().getName() + " -> " + current);
            current++;
            permits[1 - parity].release();
        }
    }

}
```

```java
AlternatePrinter printer = new AlternatePrinter();
new Thread(() -> printer.print(1), "odd ").start();
new Thread(() -> printer.print(0), "even").start();
```

**省下来的代码都去哪了**：

| 其它解法必须写的 | 这里由什么替代 |
|---|---|
| `while (current <= MAX)` 边界判断 | `for (int i = 0; i < 50; i++)`，每人恰好 50 个数，边界天然确定 |
| 用 `while` 重新确认条件 | 通行证是硬授权，拿到就一定轮得到自己，没有虚假唤醒 |
| 退出前再 `notifyAll` 一次 | 每人打完自己的 50 个就自然结束，不需要叫醒谁 |
| `finally { lock.unlock(); }` | 压根没用锁，信号量自己管释放 |

`current++` 也不用额外同步：同一时刻只有持有通行证的那个线程进得来。

### 同样思路换成 synchronized 要写多少

```java
public synchronized void print(int parity) throws InterruptedException {
    while (current <= MAX) {
        while (current <= MAX && current % 2 != parity) {
            wait();
        }
        if (current > MAX) {
            break;
        }
        System.out.println(Thread.currentThread().getName() + " -> " + current);
        current++;
        notifyAll();
    }
    notifyAll();
}
```

18 行。多出来的全是 `while` / `if` / `notifyAll`——这是 `wait/notify` 的固定开销。

注意内层 `while` 必须带上 `current <= MAX`：否则最后被唤醒时 `current` 已经是 101，
「轮到自己」的判断恰好成立，就会多打印出一个 101。

### 什么时候不能用这个写法

**前提是每个线程打印的次数事先确定**（这里恰好都是 50 次）。
如果次数不确定——比如三个线程打印到某个外部条件才停——就得回到 `while` + 条件判断的版本。

> 实测：单方法版两种写法、两种启动顺序共 4 组，输出都严格是 1 到 100，无多打、无漏打、无死锁。

---

## 反面剖析：一个看着更短、实则不对的写法

网上流传很广的一版写法，比上面所有版本都短：

```java
static int count = 0;
static final Object OBJECT = new Object();

static class Printer implements Runnable {

    @Override
    public void run() {
        while (count <= 100) {              // ← 检查在锁外
            synchronized (OBJECT) {
                System.out.println(Thread.currentThread().getName() + ":" + count++);
                OBJECT.notify();            // ← 只叫醒一个
                if (count <= 100) {         // 为了让程序能正常结束
                    OBJECT.wait();
                }
            }
        }
    }

}

new Thread(new Printer(), "偶数线程").start();
new Thread(new Printer(), "奇数线程").start();
```

思路是对的（一个 `Runnable`、打印完唤醒对方再等待），`if` 那个判断也是作者有意识地防止末尾挂住。
但跑起来会发现两件事：

**问题一：打印的是 0 到 100，共 101 个数。**
`count` 从 0 开始，第一次打印的就是 0；循环到打印完 100 才停，自然多了一个。
本地跑 300 轮，**无一例外都是 101 个**，`首=0，尾=100`。改成 `count = 1` 就对了。

**问题二：线程名和实际打印的是反的。**
实测输出：

```
总输出：100
前 6 条：[偶数线程->1, 奇数线程->2, 偶数线程->3, 奇数线程->4, ...]
偶数线程打印了奇数的次数：50
奇数线程打印了偶数的次数：50
```

这个实现**根本不区分奇偶**，谁打印哪个数全看谁抢到锁，只是碰巧严格交替。
所以「偶数线程」打的全是奇数。想要真正的奇偶分工，必须把奇偶作为分支条件写进去。

**两个隐患（理论存在，本机没跑出来）**：

1. `while (count <= 100)` 在锁外，`count++` 在锁内，而且 `count` 没有 `volatile`——
   检查与自增不是原子的，线程可能拿着过期的检查结果进入临界区，多打印出一个 101。
   本机用 `yield` 放大窗口也没复现（因为每个线程拿到锁后必定打印一次），
   但这是典型的「检查与执行分离」，换个环境就可能暴雷。
2. `notify()` 只唤醒一个等待者。这里侥幸不死（每个线程都是先打印再等待），
   本机三个线程跑 100 轮也全部正常；但只要改成「可能多个线程同时在等」的结构，就会漏唤醒。

另外 `e.printStackTrace()` 把中断吞了，正确做法是 `Thread.currentThread().interrupt()` 后退出。

### 改成正确的最短版（保留原结构）

```java
static final int MAX = 100;

static final Object LOCK = new Object();

static int count = 1;

static class Printer implements Runnable {

    private final int parity;

    Printer(int parity) {
        this.parity = parity;
    }

    @Override
    public void run() {
        while (true) {
            synchronized (LOCK) {
                while (count <= MAX && count % 2 != parity) {   // 判断挪进锁里
                    try {
                        LOCK.wait();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (count > MAX) {
                    LOCK.notifyAll();                           // 退出前叫醒对方
                    return;
                }
                System.out.println(Thread.currentThread().getName() + " 打印:" + count++);
                LOCK.notifyAll();
            }
        }
    }

}
```

```java
new Thread(new Printer(0), "偶数线程").start();
new Thread(new Printer(1), "奇数线程").start();
```

实测两种启动顺序：**个数=100、严格 1 到 100、奇偶错位 0、两个线程都正常退出**。

看出改动的三处：判断挪进 `synchronized`、`notify` 换 `notifyAll`、`wait` 用 `while` 包住。
这正好对应开头说的三个坑——不是代码越短越好，是**该有的检查一处都不能少**。

---

## 反面教材：不要自旋忙等

```java
while (current % 2 == parity) {
    // 空转，什么都不做
}
System.out.println(current);
```

两个线程同时空转，CPU 直接打满两个核，数字还可能打印错（判断和打印之间有窗口）。
**等待就该让出 CPU**：`wait` / `await` / `acquire` / `park` 都行，空转不行。

---

## 四种解法怎么选

| 解法 | 同步手段 | 唤醒精度 | 条件判断 | 适用场景 |
|---|---|---|---|---|
| `synchronized` + `wait/notifyAll` | 内置锁 | 广播，靠条件过滤 | 必须 `while` | 面试必写、代码最少 |
| `ReentrantLock` + `Condition` | 显式锁 | **精准**，只叫该叫的 | 必须 `while` | 生产首选，多线程轮流时优势明显 |
| `Semaphore` | 信号量 | 精准（一对一交棒） | 不需要 | 顺序固定的交替场景，最好读 |
| `LockSupport` | `park/unpark` | 精准（指定线程） | 必须 `while` | 框架底层，业务代码少用 |

---

## 记忆口诀

**「一锁二判三唤醒」**：

1. **锁**：所有对共享数字的读写都在临界区里
2. **判**：判断条件用 `while`，被唤醒后重新确认
3. **唤**：干完活叫醒对方，然后**自己去等**；退出前再叫一次，别把对方落下

再加一句：**打印和自增必须绑在一起**，中间不能有任何可能让出 CPU 的空隙。

---

## 判断标准

写完代码问自己三句，三句都过才算对：

1. 把 `while` 换成 `if`，会不会出错？（会 → 说明确实需要 `while`）
2. 两个线程启动顺序反过来，结果还对吗？（对 → 说明不依赖启动顺序）
3. 打印到 100 之后，另一个线程能正常退出吗？（能 → 说明退出前唤醒了对方）

**延伸**：把「两个线程」改成「三个线程轮流打印 A/B/C」，只有解法二、三、四能平滑扩展；
解法一的 `notifyAll` 虽然也能跑，但唤醒是广播，线程一多就全是无效的唤醒-检查循环。
