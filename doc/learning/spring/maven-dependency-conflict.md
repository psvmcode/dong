# Maven 依赖冲突：为什么会冲突，怎么解决

> 目标：说清冲突怎么产生的、怎么查、怎么治，以及为什么 `dependencyManagement` 是首选方案。

---

## 一句话

**依赖会传递——你只声明了 A，Maven 却把 A 依赖的 B、C、D 全拉了进来。
当不同路径拉起同一个库的不同版本时，Maven 只能挑一个，挑错就冲突。**

冲突的本质：**同一个类，在 classpath 上出现了两个不同版本，运行时用错了那个。**

---

## 一、冲突是怎么产生的

### 依赖传递

```xml
<!-- 你只写了这一行 -->
<dependency>
    <groupId>com.example</groupId>
    <artifactId>A</artifactId>
    <version>1.0</version>
</dependency>
```

实际拉进来的：

```
A:1.0
 ├── guava:20.0        ← A 依赖的
 └── B:2.0
      ├── guava:25.0   ← B 依赖的（版本不同！）
      └── commons-lang3:3.9
```

`guava` 出现了两个版本，Maven 必须**二选一**。

### 版本仲裁：Maven 怎么选

Maven 有两条规则，**按顺序**判断：

| 规则 | 含义 |
|---|---|
| **① 最短路径优先** | 谁离你近用谁。你直接声明的（路径长度 1）永远赢过传递来的（长度 2） |
| **② 先声明优先** | 路径长度相同时，`<dependencies>` 里**先写**的那个赢 |

上面例子里 `guava:20.0` 路径长度是 2，`guava:25.0` 是 3 → 选 **20.0**。

**关键问题**：Maven 选的是「路径最短」，**不是版本最高**。
所以完全可能选中一个**更老的、缺方法的**版本 —— 这就是冲突的根源。

### 哪些依赖会被传递

不是所有依赖都会传下去，看 **scope**：

| scope | 是否传递 | 典型用途 |
|---|---|---|
| `compile`（默认） | ✅ 传递 | 编译期和运行期都要 |
| `runtime` | ✅ 传递 | 运行期要（如 JDBC 驱动） |
| `provided` | ❌ 不传递 | 容器提供（如 servlet-api） |
| `test` | ❌ 不传递 | 只在测试里 |
| `system` / `import` | ❌ | 特殊用途 |

**`provided` 不传递**这点很重要：写 starter 类库时，把 Spring 相关依赖设为
`provided`，避免污染使用方的依赖树。

---

## 二、冲突的典型表现

冲突不会在编译期报错（**编译用的是你声明的版本**），而是在**运行时**炸：

| 异常 | 场景 |
|---|---|
| `NoSuchMethodError` | 调用的方法在新版本里才有，但实际加载的是老版本 |
| `ClassNotFoundException` | 需要的类在选中的版本里根本不存在 |
| `NoClassDefFoundError` | 类能编译（编译期有），运行期没有 |
| `AbstractMethodError` | 接口新增了方法，老实现类没实现 |
| `LinkageError` / `VerifyError` | 字节码版本不兼容 |

**最坑的一点**：这些错误往往在**某个特定功能**被调用时才出现，
平时跑得好好的，上线后偶发崩溃。而且堆栈里看不出是依赖问题。

> 记忆点：**编译期不报错、运行期才炸的异常，优先怀疑依赖冲突。**

---

## 三、怎么查

### 命令一：看依赖树（最常用）

```bash
mvn dependency:tree
```

输出形如：

```
[INFO] com.dong:dong:jar:1.0
[INFO] +- org.springframework.boot:spring-boot-starter-web:jar:3.2.0
[INFO] |  +- org.springframework:spring-web:jar:6.1.1
[INFO] |  \- org.springframework:spring-webmvc:jar:6.1.1
[INFO] \- com.example:A:jar:1.0
[INFO]    \- com.google.guava:guava:jar:20.0        ← 注意版本
```

### 命令二：显示被省略的（关键）

```bash
mvn dependency:tree -Dverbose
```

会标出**被仲裁掉**的版本：

```
[INFO] |  \- (com.google.guava:guava:jar:25.0:compile - omitted for conflict with 20.0)
                                                  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                                                  这一行才是冲突的线索
```

**不加 `-Dverbose` 是看不到被丢弃的版本的**，很多人查不出冲突就是因为漏了这个参数。

### 命令三：查某个库的来源

```bash
mvn dependency:tree -Dincludes=com.google.guava:guava
```

**只显示 guava 相关路径**，一眼看清是谁把它拉进来的。

### 命令四：IDEA 图形化

右侧 **Maven 面板 → 依赖树图标**，或者 `pom.xml` 里右键 → **Diagrams → Show Dependencies**。
冲突的依赖会**标红**，比命令行直观。

### 命令五：分析未使用的依赖

```bash
mvn dependency:analyze
```

会报告「声明了但没用到」和「用到了但没声明」的依赖，顺带清理。

---

## 四、解决方案（按推荐度排序）

### 方案一：`dependencyManagement` 统一版本 ⭐ 首选

**不动依赖声明，只在父 pom 里统一指定版本**：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
            <version>33.0.0-jre</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**原理**：`dependencyManagement` 里的声明**不引入依赖**，只做**版本约束**。
所有传递进来的 guava 都会被强制改成 33.0.0。

| 优点 | 说明 |
|---|---|
| **集中管理** | 版本只写一处，全项目统一 |
| **不侵入** | 不用改每个子模块的依赖声明 |
| **优先级最高** | 覆盖传递依赖和直接声明 |

**这是 Spring Boot 项目的标准做法**——
`spring-boot-dependencies` 本身就是一份巨大的 `dependencyManagement`。

### 方案二：排除传递依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>A</artifactId>
    <version>1.0</version>
    <exclusions>
        <exclusion>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**适用**：某个传递依赖**完全不需要**，或者它带来了明确的冲突源。

**缺点**：**要逐个排除**，而且依赖升级后可能带来新的传递依赖。
**不如 `dependencyManagement` 省事**，只在明确知道要剔除某个库时用。

### 方案三：直接声明（利用就近原则）

```xml
<!-- 直接声明，路径长度 1，必然赢过传递来的 -->
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>33.0.0-jre</version>
</dependency>
```

**原理**：靠「最短路径优先」压过传递版本。

**缺点**：如果多个模块都这么做，版本会**散落各处**，难以统一。不如方案一。

### 方案四：BOM（推荐用于多模块）

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

`<scope>import</scope>` 表示**把对方 pom 里的 dependencyManagement 导入进来**。

**这就是「BOM（Bill of Materials）」**：一个只含版本声明、不含代码的 pom，
用来统一一族组件的版本。Spring Boot、Spring Cloud、Netty 都提供 BOM。

**多模块项目用它最省事**——各子模块不用写版本号。

### 方案五：`<optional>true</optional>`

```xml
<dependency>
    <groupId>xxx</groupId>
    <artifactId>yyy</artifactId>
    <version>1.0</version>
    <optional>true</optional>
</dependency>
```

标记为「可选依赖」，**不会再传递给使用方**。

**适用**：写公共类库时，某个依赖只在部分功能里用，不想强加给使用方。

### 方案六：强制检查（预防）

用 `maven-enforcer-plugin` 在构建时**直接失败**，把问题挡在上线前：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>enforce</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <!-- 禁止依赖收敛（同一库出现多版本） -->
                    <dependencyConvergence/>
                    <!-- 禁止特定版本 -->
                    <bannedDependencies>
                        <excludes>
                            <exclude>com.google.guava:guava:*:*:*</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**`dependencyConvergence` 规则很有用**：只要依赖树里同一库出现多个版本就**构建失败**，
逼着你解决，而不是等到运行时报 `NoSuchMethodError`。

> **这是「兜底」的思路**——把问题挡在构建阶段，而不是运行阶段。
> 和代码里的参数校验是一个道理。

---

## 五、Spring Boot 项目怎么处理

**Spring Boot 已经帮你解决了大部分版本问题**：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>
```

`starter-parent` 的父 pom 引入了 `spring-boot-dependencies`，
里面用 `dependencyManagement` 锁定了**几百个常用库的版本**。

所以：

1. **引入 Spring 生态的库，不要写版本号**——父 pom 已经管好了
2. **要改版本，用属性覆盖**（父 pom 里定义的属性名）：

```xml
<properties>
    <!-- 覆盖 Spring Boot 默认的版本 -->
    <guava.version>33.0.0-jre</guava.version>
</properties>
```

3. **引入非 Spring 生态的库**，才需要自己写版本并放 `dependencyManagement`

---

## 六、排查与解决的标准流程

```
1. 现象：运行时报 NoSuchMethodError / ClassNotFoundException
        ↓
2. 确认是哪个类
   从异常堆栈里找到类名和包名
        ↓
3. 反查是哪个库提供的
   mvn dependency:tree -Dincludes=包名
        ↓
4. 看有没有多版本
   mvn dependency:tree -Dverbose | grep 库名
   （关键：必须加 -Dverbose）
        ↓
5. 在 dependencyManagement 里统一版本
        ↓
6. 重跑，确认树里只剩一个版本
   mvn dependency:tree -Dincludes=库名
        ↓
7. （可选）加 enforcer 插件防止复发
```

---

## 七、最佳实践

| 实践 | 理由 |
|---|---|
| **用 `dependencyManagement` 统一，不用 exclusions** | 集中一处，改动小 |
| **用 BOM 管理一族组件** | 多模块项目最省事 |
| **Spring Boot 项目不写版本号** | 父 pom 已统一 |
| **引入新依赖后跑一次 `dependency:tree`** | 提前发现冲突 |
| **加 `maven-enforcer-plugin` 的 `dependencyConvergence`** | **构建期拦截，而不是运行期崩溃** |
| 定期 `mvn dependency:analyze` | 清理无用依赖 |

**一句话**：

> 冲突的根源是「**依赖传递 + 版本仲裁选了老版本**」，
> 解法是「**用 `dependencyManagement` 把版本收口到一处**」，
> 防线是「**让 enforcer 在构建时直接失败**」。

---

## 常见误区

**误区一：以为 `mvn clean install` 成功了就没冲突**
编译期用的是你声明版本的**API 签名**，运行时加载的却是被仲裁后的版本。
**编译通过 ≠ 运行时没问题**，这正是问题难以发现的原因。

**误区二：以为 Maven 会选最新版本**
Maven 选的是**路径最短**的，不是版本最高的。
所以经常出现「明明依赖树里有新版本，实际用的却是老版本」。

**误区三：用 exclusions 逐个排除**
治标不治本，而且依赖升级后可能带来新的传递依赖。
**优先用 `dependencyManagement`**。

**误区四：在子模块里重复声明版本**
版本散落各处，改一处漏一处。**统一放父 pom 的 `dependencyManagement`**。

---

## 记忆口诀

| 问题 | 答案 |
|---|---|
| 为什么冲突 | **依赖传递**导致同一库多个版本，Maven 只能选一个 |
| 怎么选 | **① 最短路径优先 ② 路径相同则先声明优先** |
| 选的是最新版吗 | **不是**，是路径最短的（经常是老版本） |
| 什么时候爆 | **运行期**（编译期不报），典型 `NoSuchMethodError` |
| 怎么查 | `mvn dependency:tree -Dverbose`（**必须加 verbose**） |
| 首选解法 | **`dependencyManagement` 统一版本** |
| 多模块怎么管 | **BOM + `<scope>import</scope>`** |
| 怎么防复发 | **`maven-enforcer-plugin` 的 dependencyConvergence** |
| Spring Boot 里写版本吗 | **不写**，父 pom 已统一；要改就用 properties 覆盖 |
