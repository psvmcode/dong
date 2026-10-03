# Spring 事务：@Transactional 什么时候会失效

> 目标：说清它靠什么生效，以及九个常见的失效场景。

---

## 一句话

**`@Transactional` 是靠 AOP 动态代理生效的——凡是代理「管不到」的情况，事务就失效。**

理解了这句话，九个失效场景基本都能推出来，不用死记。
代理机制见 [dynamic-proxy.md](../java/dynamic-proxy.md)。

---

## 它生效的原理

```
你写的代码：
    @Transactional
    public void transfer() { ... }

Spring 实际做的：
    代理对象.transfer() {
        开启事务;                     ← 代理插入的
        真实对象.transfer();          ← 你的业务逻辑
        提交事务 或 回滚;              ← 代理插入的
    }
```

**关键**：必须通过**代理对象**调用，才能走到增强逻辑。

---

## 九个失效场景

### 1. 方法是非 public 的

```java
@Transactional
private void transfer() { }      // ❌ 不生效
```

**原因**：Spring AOP 的代理（无论 JDK 还是 CGLIB）**无法代理 private 方法**。
`protected`、`package-private` 在 CGLIB 下可以，但 JDK 代理不行——
**统一用 public 最稳妥**。

### 2. 同类内部调用（最常见）

```java
@Service
public class OrderService {
    public void create() {
        this.pay();          // ❌ 这样调，pay() 的事务不生效
    }

    @Transactional
    public void pay() { }
}
```

**原因**：`this` 是**真实对象**，不是代理对象。
这个坑在 [dynamic-proxy.md](../java/dynamic-proxy.md) 里详细说过。

**解法**：

```java
// 方案一：注入自己（加 @Lazy 避免循环依赖）
@Autowired @Lazy
private OrderService self;

public void create() {
    self.pay();          // ✅ 走代理
}

// 方案二：用 AopContext（需要 @EnableAspectJAutoProxy(exposeProxy = true)）
((OrderService) AopContext.currentProxy()).pay();

// 方案三：拆成两个类（最推荐，结构也更清晰）
```

### 3. 异常被 catch 吞掉了

```java
@Transactional
public void transfer() {
    try {
        // 数据库操作
        throw new RuntimeException("余额不足");
    } catch (Exception e) {
        log.error("失败了", e);       // ❌ 吞掉了，事务会正常提交！
    }
}
```

**原因**：代理的逻辑是「捕获到异常 → 回滚」。
你在方法内部把异常 catch 了，代理**根本不知道出错了**，
于是正常提交事务。

**解法**：catch 后**重新抛出**，或手动标记回滚：

```java
catch (Exception e) {
    log.error("失败了", e);
    throw e;                                          // 方案一：抛出去
    // TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();  // 方案二
}
```

### 4. 抛出的异常类型不对

```java
@Transactional
public void transfer() throws Exception {
    throw new Exception("受检异常");     // ❌ 默认不回滚！
}
```

**原因**：Spring 默认**只对 `RuntimeException` 和 `Error` 回滚**，
受检异常（Exception）**默认提交**。

**解法**：显式指定：

```java
@Transactional(rollbackFor = Exception.class)     // 所有异常都回滚
```

**建议：生产环境一律加 `rollbackFor = Exception.class`**，
别依赖默认值，太容易踩。

### 5. 方法被 final 修饰

```java
@Transactional
public final void transfer() { }     // ❌ CGLIB 无法代理
```

**原因**：CGLIB 靠**继承 + 重写**实现代理，`final` 方法不能被重写。
（Boot 2.x 起默认 CGLIB，所以这个坑实际会遇到）

### 6. 类没有被 Spring 管理

```java
// 没有 @Service / @Component 注解
public class OrderService {
    @Transactional
    public void transfer() { }
}
```

**原因**：不在 Spring 容器里，就没有 BeanPostProcessor 给它生成代理，
`@Transactional` 只是个普通注解。

### 7. 数据库引擎不支持事务

**MySQL 的 MyISAM 引擎不支持事务**，用 InnoDB。

```sql
show table status where name = 'your_table';   -- 看 Engine 字段
```

### 8. 传播行为配置错误

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)   // ❌ 挂起事务，以非事务方式跑
@Transactional(propagation = Propagation.NEVER)           // ❌ 有事务就报错
```

### 9. 多线程调用

```java
@Transactional
public void transfer() {
    new Thread(() -> {
        // 这里的代码不受外层事务控制 ❌
    }).start();
}
```

**原因**：事务信息绑定在**线程**上（`ThreadLocal`），
新线程里没有这个上下文。

---

## 七个传播行为

**传播行为**解决的是：「当前方法被调用时，外层已经有事务了，我怎么办？」

| 传播行为 | 行为 | 常用度 |
|---|---|---|
| **REQUIRED**（默认） | 有就加入，没有就新建 | ⭐⭐⭐ 最常用 |
| **REQUIRES_NEW** | **挂起当前事务，另起一个新的** | ⭐⭐ 独立事务（如日志） |
| SUPPORTS | 有就加入，没有就以非事务跑 | ⭐ |
| NOT_SUPPORTED | 挂起事务，以非事务方式跑 | ⭐ |
| MANDATORY | 必须有，没有就报错 | |
| NEVER | 必须没有，有就报错 | |
| **NESTED** | 嵌套事务，外层回滚内层也回滚，<br>但**内层回滚不影响外层** | ⭐⭐ |

**REQUIRED 和 REQUIRES_NEW 的区别**（常考）：

```java
@Transactional
public void outer() {
    inner();          // 如果是 REQUIRED：inner 加入 outer 的事务
                      // 如果是 REQUIRES_NEW：inner 挂起 outer，自己开一个
    throw new RuntimeException();
}

// REQUIRED：inner 跟着一起回滚
// REQUIRES_NEW：inner 【已经提交了】，不受 outer 回滚影响
```

**REQUIRES_NEW 的典型用途**：记录操作日志。
不管主业务成功失败，日志都要留下来。

---

## 事务隔离级别

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
```

| 级别 | 说明 |
|---|---|
| `DEFAULT` | **用数据库的**（MySQL 默认是可重复读） |
| `READ_UNCOMMITTED` | 读未提交 |
| `READ_COMMITTED` | 读已提交 |
| `REPEATABLE_READ` | 可重复读 |
| `SERIALIZABLE` | 串行化 |

一般用 `DEFAULT`（即交给数据库决定）。
具体含义见 [`mysql/transaction-isolation.md`](../mysql/transaction-isolation.md)。

---

## 最佳实践

| 建议 | 原因 |
|---|---|
| **一律加 `rollbackFor = Exception.class`** | 避免受检异常不回滚 |
| **事务方法尽量短** | 长事务占着数据库连接，容易锁等待、拖垮连接池 |
| **不要在事务里做远程调用** | 网络延迟会让事务时间不可控；且远程服务无法回滚 |
| **不要在事务里做大量查询** | 占用连接，且可能读到不必要的快照 |
| 给事务方法加 `timeout` | 防止长事务拖死数据库 |
| 类上加 `@Transactional` 要谨慎 | 会给所有 public 方法都加事务，包括纯查询方法 |

**「不要在事务里做远程调用」是重点**：

```java
@Transactional
public void createOrder() {
    db.insert(order);
    paymentService.pay();      // ❌ 远程调用，可能超时
    smsService.send();         // ❌ 发短信也在事务里
}
```

远程调用慢会**长时间占用数据库连接**，
而且**远程服务不会因为你回滚就撤销**——它的操作已经生效了。

**正确做法**：先落库，提交事务后，再异步调用远程服务。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 靠什么生效 | **AOP 动态代理** |
| 最核心的失效原因 | 没走**代理对象** |
| 同类调用为什么不生效 | `this` 是真实对象不是代理 |
| 默认回滚哪些异常 | 只回滚 **RuntimeException 和 Error** |
| 建议怎么写 | `@Transactional(rollbackFor = Exception.class)` |
| 默认传播行为 | **REQUIRED**（有就加入，没有就新建） |
| REQUIRES_NEW 干什么 | 挂起外层，另起独立事务（**内层提交不受外层影响**） |
| 事务里别干什么 | **远程调用**、大量查询、耗时操作 |
