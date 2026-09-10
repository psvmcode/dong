# 顺序消息：全局有序和分区有序

> 目标：说清为什么会乱序、怎么保证顺序，以及那个最容易踩的坑。

---

## 一句话

**全局有序代价太大（退化成单线程），实际用的是「分区有序」——保证同一业务 key 的消息按顺序即可。**

---

## 为什么会乱序

消息从生产到消费，中间有三处会打乱顺序：

```
生产者 ──①──> Broker ──②──> 消费者

① 多分区：消息 A 进了分区 0，消息 B 进了分区 1
   → 分区之间没有任何顺序保证

② 多消费者：一个消费者组有多个实例，各消费不同分区
   → 实例之间无法协调先后顺序

③ 消费失败重试：第 2 条失败进入重试队列，第 3 条先消费成功
   → 顺序颠倒（最容易忽略的坑）
```

---

## 全局有序 vs 分区有序

### 全局有序

**要求**：整个 Topic 的所有消息严格按发送顺序消费。

**实现方式**：Topic 只配 **1 个分区**，消费者只开 **1 个线程**。

**代价**：
- 吞吐量**极低**（失去了 MQ 并行处理的所有优势）
- 无法水平扩展

**结论**：**几乎不用**。只有在数据量极小、且严格依赖全局顺序时才考虑。

### 分区有序（实际用的）

**要求**：同一业务 key 的消息保证顺序，**不同 key 之间不需要有序**。

| 场景 | 业务 key |
|---|---|
| 订单状态流转 | **订单号**（创建→支付→发货，同一订单必须有序） |
| 数据同步 binlog | **主键**（同一行的增删改必须有序） |
| 账户流水 | **账户号** |

不同订单之间可以乱序——订单 A 和订单 B 的处理顺序无关紧要，
但**订单 A 自己的三条消息必须按顺序**。

**这正是绝大多数业务的需求**，而且可以并行处理不同 key，性能不受影响。

---

## 怎么实现分区有序

### 第一步：生产端——同一 key 发到同一分区

**核心**：不要用默认的轮询分配，要**按业务 key 路由**。

```java
// RocketMQ：用 MessageQueueSelector 按订单号选择队列
producer.send(msg, new MessageQueueSelector() {
    @Override
    public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
        String orderId = (String) arg;
        int index = Math.abs(orderId.hashCode()) % mqs.size();
        return mqs.get(index);              // 同一订单号 → 同一个队列
    }
}, orderId);
```

```java
// Kafka：指定 key，Kafka 自动按 key 哈希到分区
producer.send(new ProducerRecord<>(topic, orderId, message));
```

**生产端还必须是同步发送**——异步发送无法保证发送顺序
（回调是并发的，第 2 条可能比第 1 条先到）。

```java
// ❌ 异步发送不保证顺序
producer.send(msg, callback);

// ✅ 同步发送
producer.send(msg);
```

### 第二步：消费端——一个队列一个线程

**核心**：同一个队列的消息，必须被**同一个线程串行消费**。

```java
// RocketMQ：用 MessageListenerOrderly（而不是 Concurrently）
consumer.registerMessageListener(new MessageListenerOrderly() {
    @Override
    public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs,
                                               ConsumeOrderlyContext context) {
        for (MessageExt msg : msgs) {
            process(msg);        // 串行处理
        }
        return ConsumeOrderlyStatus.SUCCESS;
    }
});
```

`MessageListenerOrderly` 会给每个队列**加锁**，
保证同一时刻只有一个线程消费某个队列。

**Kafka**：一个 Partition 只能被消费者组里的**一个实例**消费，
实例内部用单线程消费该 Partition，天然有序。

---

## 最容易踩的坑：重试破坏顺序

**场景**：

```
订单消息：1(创建) 2(支付) 3(发货)
第 2 条处理失败
```

如果用**普通重试**（失败的消息进入重试队列，稍后重新消费）：

```
时刻1：消费 1 ✅ → 消费 2 ❌ 失败 → 消费 3 ✅
时刻2：消费 2（重试）✅

实际顺序：1 → 3 → 2     ← 乱了！
```

**为什么**：错误消息被投递到**重试队列**，
而后面的消息继续在**原队列**消费，两条线并行了。

### 解法

**消费失败时，不要跳过，要「挂起当前队列」**：

```java
// RocketMQ 的正确做法
public ConsumeOrderlyStatus consumeMessage(List<MessageExt> msgs,
                                           ConsumeOrderlyContext context) {
    for (MessageExt msg : msgs) {
        try {
            process(msg);
        } catch (Exception e) {
            // 不是跳过，而是【暂停这个队列一会儿】，等下再消费同一批
            return ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT;
        }
    }
    return ConsumeOrderlyStatus.SUCCESS;
}
```

`SUSPEND_CURRENT_QUEUE_A_MOMENT` 的含义是：
**「这批消息我先不确认，稍等一下让我重试」**，
后面的消息会**等在这儿**，不会跑到前面去。

**代价**：一条消息卡住，整个队列都卡住。
所以顺序消息对**消费失败的处理要格外谨慎**：

- 失败次数达到上限后，不能再无限挂起，要转**死信队列**并告警
- 业务逻辑要尽量保证不失败（前置校验充分）

---

## 各 MQ 的支持情况

| MQ | 顺序保证 | 实现 |
|---|---|---|
| **RocketMQ** | 分区有序 | `MessageQueueSelector` + `MessageListenerOrderly` |
| **Kafka** | 分区有序 | 指定 key → 同一 Partition；单线程消费 |
| **RabbitMQ** | 较弱 | 单队列单消费者；多消费者会乱序（需拆队列） |

**RocketMQ 在顺序消息上做得最好**，有专门的 API 和失败挂起机制。

---

## 代价总结

| 问题 | 影响 |
|---|---|
| 同一 key 只能在一个队列 | 热点 key 会导致**队列倾斜** |
| 消费失败会阻塞整个队列 | 需要死信队列 + 告警兜底 |
| 生产端必须同步发送 | 吞吐量下降 |
| 消费端单线程 | 无法靠加线程提速 |

**顺序消息本质上是「用并行度换顺序」**。
能并行的是不同 key 之间，同一 key 内部必须串行。

---

## 什么时候真的需要顺序消息

**先问一句：能不能不要顺序？**

很多所谓的「顺序要求」，其实可以通过**设计规避**：

| 方案 | 说明 |
|---|---|
| **消息带状态/版本号** | 消费时检查版本号，旧版本直接丢弃（**推荐**） |
| **业务上做状态机校验** | 收到「支付」但订单还是「未创建」→ 暂存或拒绝 |
| **用时间戳/序号** | 只处理最新的一条 |

比如订单状态：如果消息里有**完整状态**而不是「增量操作」，
那乱序到达也没关系——收到旧的直接丢弃即可。

**这种情况比严格顺序简单得多，性能也好得多。**

**判断标准**：

- 消息是**完整状态**（`set status = PAID`）→ 不需要顺序，加版本号即可
- 消息是**增量操作**（`balance += 100`）→ 顺序很重要，必须保序

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 全局有序 | 单分区单线程，**性能极差，基本不用** |
| 分区有序 | 同一 key 有序，**实际用的** |
| 生产端怎么做 | **按 key 路由到同一队列** + **同步发送** |
| 消费端怎么做 | **一个队列一个线程**（RocketMQ 用 Orderly 监听器） |
| 最大的坑 | 失败重试会**跳到后面** → 要「挂起队列」而非跳过 |
| 挂起的代价 | 一条卡住整个队列卡住 → 要死信队列兜底 |
| 能不能不要顺序 | 消息带**完整状态/版本号**时，可乱序，旧版本丢弃 |
