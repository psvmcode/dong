# Bean 生命周期：从 class 文件到可用对象

> 目标：说清一个 Bean 从创建到销毁经历了什么，以及在哪几步能插手。

---

## 一句话

**Bean 的生命周期就是「实例化 → 属性填充 → 初始化 → 使用 → 销毁」，
Spring 在每步之间留了扩展点让你插手。**

理解它的价值：知道**在哪一步能做什么**，
比如 `@Autowired` 什么时候生效、`@PostConstruct` 什么时候执行、AOP 代理在哪生成。

---

## 完整流程

```
1. 加载 BeanDefinition          （扫描 @Component，解析成元数据）
        ↓
2. 实例化 Instantiate            （调用构造方法，此时属性还是 null）
        ↓
3. 属性填充 Populate             （@Autowired、@Value 注入）
        ↓
4. 初始化 Initialize             （最复杂的一步）
        ↓
5. 使用
        ↓
6. 销毁 Destroy                  （容器关闭时）
```

前两步合起来就是「创建对象」，但**中间隔着属性填充**——
这个顺序很关键。

---

## 第 1 步：加载 BeanDefinition

Spring 扫描 classpath，找到 `@Component`（含 `@Service`、`@Controller`、`@Repository`），
把类的信息（类名、作用域、是否懒加载、依赖关系）解析成 `BeanDefinition` 对象，注册到容器。

**注意**：这时候**还没有创建对象**，只是登记了「有这么个类」。

---

## 第 2 步：实例化

通过**反射调用构造方法**创建对象。

```java
Object bean = clazz.getDeclaredConstructor().newInstance();
```

**此时对象已经存在，但所有字段都是默认值（null / 0）**——
`@Autowired` 的字段还没注入。

**这就是为什么不能在构造方法里用 @Autowired 的字段**：

```java
@Service
public class UserService {
    @Autowired
    private OrderService orderService;

    public UserService() {
        // ❌ 此时 orderService 还是 null！构造早于属性注入
        orderService.init();
    }
}
```

**要用注入的字段做初始化，放到 `@PostConstruct` 里（第 4 步）。**

---

## 第 3 步：属性填充

处理 `@Autowired`、`@Value`、`@Resource` 等注解，把依赖注入进去。

**如果遇到循环依赖，就在这一步解决**——
详见 [circular-dependency.md](circular-dependency.md)。

---

## 第 4 步：初始化（最重要）

这是扩展点最多的一步，按顺序执行：

```
4.1 执行各种 Aware 接口
     BeanNameAware.setBeanName()
     BeanFactoryAware.setBeanFactory()
     ApplicationContextAware.setApplicationContext()
        ↓
4.2 BeanPostProcessor 前置处理
     postProcessBeforeInitialization()
        ↓
4.3 初始化方法
     @PostConstruct 注解的方法        ← 最常用
     InitializingBean.afterPropertiesSet()
     自定义的 init-method
        ↓
4.4 BeanPostProcessor 后置处理
     postProcessAfterInitialization()   ← AOP 代理在这里生成！
```

### 关键扩展点

| 扩展点 | 时机 | 典型用途 |
|---|---|---|
| `@PostConstruct` | 属性注入**之后** | 用注入的依赖做初始化（最常用） |
| `InitializingBean` | `@PostConstruct` 之后 | 同上，但侵入性强（要 implements） |
| `BeanPostProcessor` | 初始化前后 | **AOP 代理**、`@Autowired` 的实现、Aware 注入 |

### AOP 代理在哪生成

**在 `postProcessAfterInitialization()`**——初始化的**最后**。

这解释了动态代理相关的一个事实：
**代理对象是 Bean 完全初始化之后才生成的**，
所以代理对象持有的是**完整的真实对象**。

---

## 第 5 步：使用

Bean 在单例池（singletonObjects）里，被各处注入使用。

**单例 Bean 在容器启动时就创建好了**（除非标了 `@Lazy`）。
这也是 Spring 启动慢的原因之一——它一次性把所有单例 Bean 都建好。

---

## 第 6 步：销毁

容器关闭（`context.close()`）时：

```
@PreDestroy 注解的方法
    ↓
DisposableBean.destroy()
    ↓
自定义的 destroy-method
```

**典型用途**：关闭线程池、释放连接、注销注册中心。

```java
@PreDestroy
public void destroy() {
    executor.shutdown();      // 释放资源
}
```

**注意**：**只有容器正常关闭才会调用**。
`kill -9` 强杀进程是不会执行的。

---

## 作用域

| 作用域 | 说明 | 生命周期 |
|---|---|---|
| **singleton**（默认） | 整个容器**一个实例** | 随容器创建和销毁 |
| **prototype** | 每次 `getBean()` **新建一个** | **容器不管销毁**，要自己释放 |
| request | 每个 HTTP 请求一个 | 请求结束销毁 |
| session | 每个会话一个 | 会话过期销毁 |
| application | 每个 ServletContext 一个 | — |

**prototype 的坑**：Spring **只负责创建，不负责销毁**。
创建的 prototype Bean 如果持有资源（线程、连接），**必须自己释放**，
否则就是内存泄漏。

---

## 常见疑问

### BeanPostProcessor 能做多大事

非常大，Spring 的好多功能都是靠它实现的：

| 功能 | 由哪个 BeanPostProcessor 实现 |
|---|---|
| `@Autowired` 注入 | `AutowiredAnnotationBeanPostProcessor` |
| `@PostConstruct` / `@PreDestroy` | `CommonAnnotationBeanPostProcessor` |
| **AOP 代理生成** | `AnnotationAwareAspectJAutoProxyCreator` |
| `@Configuration` 代理增强 | `ConfigurationClassPostProcessor` |

所以「BeanPostProcessor」不是个小角色，
**它是 Spring 容器最重要的扩展机制**。

### 构造方法和 @PostConstruct 选哪个

| 场景 | 用哪个 |
|---|---|
| 初始化**不依赖**注入的字段 | 构造方法 |
| 初始化**依赖**注入的字段（如查数据库预热） | `@PostConstruct` |
| 需要拿到 Bean 名字/容器 | `BeanNameAware` / `ApplicationContextAware` |

**判断标准**：需要用到 `@Autowired` 进来的东西，就放 `@PostConstruct`。

### 循环依赖在哪一步解决

在**第 3 步（属性填充）**——
实例化之后就把对象「提前暴露」出来，让对方能引用到。
详见 [circular-dependency.md](circular-dependency.md)。

---

## 记忆口诀

| 阶段 | 关键动作 |
|---|---|
| 1 加载定义 | 扫描，只登记不创建 |
| 2 实例化 | 调构造方法，**字段还是 null** |
| 3 属性填充 | `@Autowired` 生效，**循环依赖在此解决** |
| 4 初始化 | Aware → 前置处理 → **`@PostConstruct`** → 后置处理（**AOP 代理在此生成**） |
| 5 使用 | 单例在容器启动时已创建 |
| 6 销毁 | `@PreDestroy`，**`kill -9` 不执行** |

| 问题 | 答案 |
|---|---|
| 构造里能用 @Autowired 字段吗 | **不能**，构造早于注入 |
| 想用注入的字段初始化怎么办 | 放 **`@PostConstruct`** |
| AOP 代理什么时候生成 | 初始化**最后**的 BeanPostProcessor |
| prototype 谁负责销毁 | **没人管**，要自己释放 |
| 最重要的扩展机制 | **BeanPostProcessor** |
