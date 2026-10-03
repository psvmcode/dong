# 动态代理：Spring AOP 的底层

> 目标：说清 JDK 代理和 CGLIB 的区别、各自的限制，以及 Spring 怎么选。

---

## 一句话

**动态代理就是在运行时生成一个「替身对象」，让你在调用真实方法的前后插代码。**

编译期你不需要写这个替身类，它是运行时动态生成的——这就是「动态」的含义。

---

## 为什么需要它

假设要给所有 Service 方法加日志：

```java
public void createOrder() {
    log.info("开始");          // ← 每个方法都要写
    // 业务逻辑
    log.info("结束");          // ← 每个方法都要写
}
```

几十个方法就得写几十遍，而且日志逻辑和业务逻辑**混在一起**。

有了代理：

```
调用方 → 代理对象（记日志、开事务） → 真实对象（只管业务）
```

**这就是 AOP（面向切面编程）的本质**：把日志、事务、权限这些横切关注点从业务代码里剥离。

Spring 的声明式事务（`@Transactional`）就是靠动态代理实现的——
你写的方法里没有任何事务代码，但事务确实生效了。

---

## 两种实现方式

### 1. JDK 动态代理

**要求：目标类必须实现接口。**

Java 原生支持，核心是两个类：

| 类 | 作用 |
|---|---|
| `java.lang.reflect.Proxy` | 用来**生成代理对象** |
| `java.lang.reflect.InvocationHandler` | 用来**定义增强逻辑** |

```java
interface UserService {
    void create(String name);
}

class UserServiceImpl implements UserService {
    public void create(String name) {
        System.out.println("创建用户 " + name);
    }
}

// 定义增强逻辑
class LogHandler implements InvocationHandler {
    private final Object target;   // 被代理的真实对象

    LogHandler(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("【日志】调用 " + method.getName());  // 前置增强
        Object result = method.invoke(target, args);            // 调用真实方法
        System.out.println("【日志】结束 " + method.getName());  // 后置增强
        return result;
    }
}

// 生成代理对象
UserService proxy = (UserService) Proxy.newProxyInstance(
        UserService.class.getClassLoader(),
        new Class[]{UserService.class},        // 要代理的接口
        new LogHandler(new UserServiceImpl())
);
proxy.create("张三");
// 输出：
// 【日志】调用 create
// 创建用户 张三
// 【日志】结束 create
```

**原理**：运行时生成一个实现了指定接口的**新类**（`$Proxy0`），
它把所有方法调用都转发给 `InvocationHandler.invoke()`。

### 2. CGLIB

**不要求实现接口，通过继承目标类生成子类。**

```java
class UserService {          // 注意：没有接口
    public void create(String name) { ... }
}

// CGLIB 的做法
class UserService$$EnhancerByCGLIB extends UserService {   // 继承
    @Override
    public void create(String name) {
        // 前置增强
        super.create(name);        // 调用父类方法
        // 后置增强
    }
}
```

CGLIB 底层操作字节码（基于 ASM 框架），直接生成目标类的子类。

---

## 两者对比

| 对比项 | JDK 动态代理 | CGLIB |
|---|---|---|
| 要求 | 必须实现**接口** | 可以代理**普通类** |
| 实现机制 | 实现接口，反射调用 | **继承**目标类，重写方法 |
| 限制 | 只能代理接口里的方法 | **不能代理 final 类 / final 方法** |
| 生成速度 | 快 | 稍慢（要操作字节码） |
| 调用性能 | JDK 8 之后**更快** | 早期更快，现在略慢 |
| 依赖 | JDK 自带 | 需要引入 cglib 包（Spring 已内置） |

**常见的错误说法**：「CGLIB 比 JDK 代理快」。
这在 JDK 1.6 时代成立，但 **JDK 8 之后 JDK 动态代理的性能已经反超**。

---

## 各自的坑

### JDK 代理的坑：同类内部调用不走代理

```java
@Service
public class UserService {
    public void methodA() {
        this.methodB();      // ← 这样调用，methodB 的 @Transactional 不生效！
    }

    @Transactional
    public void methodB() { ... }
}
```

**原因**：`this` 是**真实对象**，不是代理对象。
只有通过代理对象调用，才会走到增强逻辑。

**解决**：注入自己（`@Lazy` 避免循环依赖）或用 `AopContext.currentProxy()`。

### CGLIB 的坑：final 和无参构造

- `final` 方法不能被重写 → 代理不了
- `final` 类不能被继承 → 代理不了
- 目标类需要有**可访问的构造方法**（Spring 4.3 起不强制要求无参构造了）

---

## Spring 怎么选

**Spring 的规则**：

1. 目标类**实现了接口** → 默认用 **JDK 动态代理**
2. 目标类**没实现接口** → 只能用 **CGLIB**
3. 设了 `proxyTargetClass = true` → **强制用 CGLIB**

```java
@EnableAspectJAutoProxy(proxyTargetClass = true)   // 强制 CGLIB
```

**Spring Boot 2.x 起默认就是 CGLIB**（内置设置了 `proxyTargetClass = true`）。

**为什么改成默认 CGLIB？**

主要因为 JDK 代理有个麻烦：代理对象只能转成**接口类型**。

```java
// JDK 代理下，这样会报 ClassCastException
UserServiceImpl bean = (UserServiceImpl) context.getBean(UserServiceImpl.class);
```

因为生成的 `$Proxy0` 只实现了 `UserService` 接口，跟 `UserServiceImpl` 没有继承关系。
而 CGLIB 生成的是子类，两种转型都行，**更少踩坑**。

---

## 静态代理 vs 动态代理

| 类型 | 说明 | 问题 |
|---|---|---|
| **静态代理** | 手动写代理类，编译期就确定 | 一个真实类要配一个代理类，类爆炸 |
| **动态代理** | 运行时生成 | 一套 Handler 能代理任意类 |

我们平时说的「代理模式」在 Spring 语境下基本都是指**动态代理**。

---

## 用在哪

| 场景 | 说明 |
|---|---|
| **Spring AOP** | 日志、权限、监控 |
| **声明式事务** | `@Transactional` 靠代理开启和提交事务 |
| **MyBatis Mapper** | Mapper 只有接口没有实现类，用 JDK 代理生成 |
| **RPC 框架** | 客户端调用「接口」，实际是代理对象发网络请求 |
| **Feign** | 声明式 HTTP 客户端，底层也是 JDK 代理 |

**MyBatis 的 Mapper 是最好的例子**：
你只写了接口和注解（或 XML），从没写过实现类，
但 `userMapper.selectById(1)` 真能查出数据——因为 MyBatis 用 JDK 动态代理
在运行时生成了实现，把方法调用翻译成 SQL 执行。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 什么是动态代理 | 运行时生成替身对象，在方法前后插代码 |
| JDK 代理要求 | 必须实现**接口** |
| CGLIB 怎么做 | **继承**目标类生成子类 |
| CGLIB 的限制 | 不能代理 **final** 类和方法 |
| 性能谁快 | JDK 8 之后 **JDK 代理更快** |
| Spring 默认用谁 | Boot 2.x 起默认 **CGLIB** |
| 同类内部调用为什么失效 | `this` 是真实对象，不是代理对象 |
