# Spring 知识点

> Spring 事务、Bean 生命周期、循环依赖相关的知识点整理。
> 写法统一：先说解决什么问题，再说机制，最后给判断标准。

---

## 文档清单

| 文档 | 主题 |
|---|---|
| [事务](transaction.md) | `@Transactional` 九个失效场景、传播行为 |
| [Bean 生命周期](bean-lifecycle.md) | 实例化→填充→初始化→销毁，扩展点在哪 |
| [循环依赖](circular-dependency.md) | 三级缓存、为什么必须三级、什么情况解决不了 |

---

## 建议阅读顺序

`bean-lifecycle` → `circular-dependency` → `transaction`

先懂生命周期，才看得懂循环依赖在哪一步解决；
事务依赖 AOP 代理，是生命周期最后一环的应用。

---

## 一条贯穿的线索

**Spring 的很多「魔法」都建立在两个机制上**：

| 机制 | 支撑了什么 |
|---|---|
| **BeanPostProcessor** | `@Autowired`、`@PostConstruct`、**AOP 代理** |
| **动态代理** | 声明式事务、AOP、MyBatis Mapper |

理解了这两个，`@Transactional` 为什么会失效、循环依赖怎么解决，
就都是它们的应用，不用死记。
