# 类加载：双亲委派，以及为什么要打破它

> 目标：说清加载过程、双亲委派的机制与目的，以及什么场景必须打破。

---

## 一句话

**双亲委派就是「有活先往上报，父加载器干不了才自己干」——目的是保证核心类库不被篡改，且类不重复加载。**

---

## 类加载的五个阶段

```
加载 → 验证 → 准备 → 解析 → 初始化
       └──── 连接 ────┘
```

| 阶段 | 做了什么 |
|---|---|
| **加载** | 通过类的全限定名找到字节码，读入内存，生成 `Class` 对象 |
| **验证** | 检查字节码是否合法（魔数、语法、语义），防止恶意字节码 |
| **准备** | 给**静态变量**分配内存并设**零值**（不是你赋的值！） |
| **解析** | 把常量池里的**符号引用**换成**直接引用**（内存地址） |
| **初始化** | 执行静态变量赋值和 `static {}` 块 |

**准备阶段最容易考**：

```java
static int a = 123;
static final int b = 123;
```

- 准备阶段：`a = 0`（零值），`b = 123`（`final` 常量，编译期就确定了）
- 初始化阶段：`a` 才被赋成 123

---

## 类什么时候会被初始化

**主动引用**（会触发初始化）：

| 场景 | 例子 |
|---|---|
| `new` 一个对象 | `new User()` |
| 访问类的静态变量/静态方法 | `User.count`、`User.init()` |
| 反射调用 | `Class.forName("com.dong.User")` |
| 初始化子类时，父类先初始化 | — |
| 启动类（main 方法所在类） | — |

**被动引用**（**不会**触发初始化）：

```java
// 1. 通过子类引用父类的静态变量 → 只初始化父类
Son.parentStaticField;      // 只有 Father 被初始化

// 2. 通过数组定义引用类 → 不初始化
User[] users = new User[10];   // User 不会被初始化

// 3. 访问编译期常量 → 不初始化（常量在编译期就进了调用方的常量池）
User.FINAL_CONSTANT;
```

第三点很实用：`static final` 的基本类型/String 常量，
编译时会直接内联到调用方，运行时跟原类都没关系了。

---

## 三层类加载器

JDK 8 及之前：

| 加载器 | 负责加载 | 说明 |
|---|---|---|
| **Bootstrap ClassLoader** | `JAVA_HOME/lib` 下的核心类库（rt.jar） | **C++ 实现**，Java 里拿不到，getParent() 返回 null |
| **Extension ClassLoader** | `JAVA_HOME/lib/ext` 下的扩展包 | Java 实现 |
| **Application ClassLoader** | **classpath** 下的类（你自己写的） | 也叫 System ClassLoader |
| 自定义 ClassLoader | 你指定路径的类 | 继承 `ClassLoader` |

JDK 9 引入模块系统后，Extension 被 **Platform ClassLoader** 取代，但思路不变。

---

## 双亲委派的工作流程

**一句话**：收到加载请求，**先委托给父加载器**，父加载器说干不了，才自己尝试加载。

```java
protected Class<?> loadClass(String name, boolean resolve) {
    synchronized (getClassLoadingLock(name)) {
        // 1. 先检查自己是不是已经加载过了
        Class<?> c = findLoadedClass(name);
        if (c == null) {
            try {
                if (parent != null) {
                    // 2. 委托给父加载器
                    c = parent.loadClass(name, false);
                } else {
                    // 3. 到顶了，交给 Bootstrap
                    c = findBootstrapClassOrNull(name);
                }
            } catch (ClassNotFoundException e) {
                // 父加载器加载不了
            }
            if (c == null) {
                // 4. 父加载器都干不了，自己加载
                c = findClass(name);
            }
        }
        return c;
    }
}
```

**流程**：

```
自定义加载器
    ↓ 委托
App ClassLoader
    ↓ 委托
Platform/Ext ClassLoader
    ↓ 委托
Bootstrap ClassLoader  ← 先由它试
    ↓ 干不了
Platform 再试
    ↓ 干不了
App 再试
    ↓ 干不了
自定义加载器自己来
```

**注意方向**：是**从下往上委托，再从上往下尝试**。

---

## 为什么要这么设计

### 1. 避免类重复加载

父加载器加载过的类，子加载器就不用再加载一遍，
保证了同一个类在 JVM 里只有**一份 Class 对象**。

### 2. 保证核心类库安全（更重要）

**这是关键**：如果没有双亲委派，
你自己写一个 `java.lang.String` 放在 classpath 下，
就会被 App ClassLoader 加载，从而**替换掉 JDK 的 String**——
整个 JVM 都能被篡改。

有了双亲委派，`java.lang.String` 的请求会一路委托到 Bootstrap，
**Bootstrap 在 rt.jar 里找到了真正的 String**，你写的那个根本没机会加载。

这就是**沙箱安全机制**。

---

## 判断两个类是否「相同」

**必须同时满足两条**：

1. 类的**全限定名**相同
2. **加载它的 ClassLoader 相同**

**光名字一样不算同一个类**。

```java
// 同一个 class 文件，用两个不同的 ClassLoader 加载
Class<?> c1 = loader1.loadClass("com.dong.User");
Class<?> c2 = loader2.loadClass("com.dong.User");

c1 == c2;        // false！
c1.equals(c2);   // false！
```

两个 Class 对象不同，互相转型会抛 `ClassCastException`。

这个特性被**用作类隔离**：
Tomcat 为每个 Web 应用配独立的 ClassLoader，
这样两个应用里都有 `com.dong.User` 但互不影响。

---

## 什么场景必须打破双亲委派

双亲委派有个**天然缺陷**：
父加载器**无法访问**子加载器加载的类（委托是单向的）。

但有些场景，上层需要调用下层的代码。

### 场景一：JDBC / SPI

```
java.sql.Driver（在 rt.jar，由 Bootstrap 加载）
    ↓ 需要加载
com.mysql.cj.jdbc.Driver（在 classpath，由 App 加载）
```

Bootstrap 加载的 `DriverManager` 要去找 classpath 下的 MySQL 驱动，
按双亲委派它**向上委托**，永远找不到下面的类。

**解决办法：线程上下文类加载器（TCCL）**

```java
Thread.currentThread().getContextClassLoader()
```

`DriverManager` 用 TCCL（默认是 App ClassLoader）去加载驱动实现，
**相当于开了个后门，让父加载器能借用子加载器**。

这是「破坏」双亲委派的典型，但属于**合理的妥协**。
JNDI、JAXP 等所有 SPI 机制都是这个套路。

### 场景二：Tomcat 的类隔离

Tomcat 要求：

- 不同 Web 应用的同名类要**互相隔离**（各自一个 ClassLoader）
- 但公共库（比如 Spring）要**共享**

所以 Tomcat 自定义了加载器，**先自己加载**，加载不到再委托给父加载器——
**故意反过来**。

### 场景三：热部署 / 热加载

要替换一个类的新版本，必须**丢弃旧的 ClassLoader 和它加载的所有类**，
用新的 ClassLoader 重新加载。

因为 JVM 里同一个「类名 + ClassLoader」只能加载一次，
想重新加载就得换一个 ClassLoader。

OSGi、Spring DevTools、Arthas 都用了这个原理。

### 怎么打破

两种办法：

| 办法 | 做法 |
|---|---|
| 重写 `loadClass()` | 改变委派逻辑（不推荐，破坏性大） |
| 重写 `findClass()` | **推荐**，只改自己加载的方式，委派逻辑不变 |

**JDK 的建议**：不要重写 `loadClass()`，只重写 `findClass()`，
这样自定义加载器依然符合双亲委派的规范。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 五个阶段 | 加载 → 验证 → 准备 → 解析 → 初始化 |
| 准备阶段赋什么值 | **零值**（`final` 常量除外） |
| 三层加载器 | Bootstrap（C++，getParent 是 null）→ Ext/Platform → App |
| 双亲委派怎么走 | **先向上委托，再向下尝试** |
| 为什么要有它 | ① 避免重复加载 ② **保护核心类库不被篡改** |
| 两个类相同要什么 | 类名相同 **+ 加载器相同** |
| 怎么打破 | 线程上下文类加载器（JDBC/SPI）、Tomcat 自定义、热部署 |
| 怎么自定义 | 重写 **`findClass()`**，别动 `loadClass()` |
