---
name: dong-standards
description: 为 dong 项目（中间件与分布式场景实验室）编写任何 Java、SQL 或 YAML 代码之前必须加载。规范由项目现有代码反向提炼：中文 Javadoc、注解与声明之间不留空行、方法与字段前后空行、Lombok 固定用法、限界上下文分层与包职责、Result 统一响应、Constants 错误码、状态枚举落库 int、SQL 关键字小写、DDL 先行建表。
---

# dong 编码规范

规范来源是 `/Users/dong/items/java/dong` 现有代码本身，不是通用最佳实践。
新旧冲突时以本文件为准：旧的 ERP/CRM 规则（Nacos、Feign、bootstrap.yaml、禁止注释、`com.dong.lab` 包名）已全部失效。

## 何时使用

在 `/Users/dong/items/java/dong` 中写任何代码前**始终**加载：Java（Controller / Service / Mapper / Entity / DTO / Enum / Config / Task / Handler）、SQL（DDL、Mapper XML）、YAML（`application.yml`）。

## 0. 项目事实（写错直接是 bug）

| 事实 | 说明 |
|---|---|
| 包名 | `com.dong.{module}`，启动类 `com.dong.DongApplication`（`@EnableScheduling` + 一堆 `exclude`） |
| 工程形态 | 单 Maven 模块，**无前端**、**无 Nacos**、**无 Feign**、**无 profile 拆分** |
| 配置 | 只有一份 `src/main/resources/application.yml`，业务配置挂在 `dong:` 前缀下，kebab-case |
| Mapper 登记 | 走主库的 Mapper 包必须写进 `config/PrimaryMybatisConfig` 的 `@MapperScan`，光加 `@Mapper` 不够 |
| DDL 权威源 | `db/schema.sql`，改完**必须跑 `./deploy/gen-initdb.sh`** 重新生成 `deploy/initdb/` 与 `deploy/initdb-replica/` |
| 中间件 | 本地不装，全连云服务器；客户端能用 Spring Boot 自动配置就别手写 Bean |
| 依赖版本 | Spring Boot 3.4.5 + JDK 21 + 原生 MyBatis XML（**不用 MyBatis-Plus**） |

## 1. 空行与骨架（硬性，最容易写错）

1. **文件末尾必须有空行**
2. **方法前后各一个空行，方法体内部不空行**（连续赋值之间也不空行）
3. **字段、常量前面有空行**；实体每个属性前都空行
4. **注解与类型声明之间不留空行**：`@Component` 下一行就是 `public class Xxx {`
5. **成员声明顺序：Javadoc → 注解 → 声明**（`/** */` 在最上，`@Annotation` 在中间）
6. 4 空格缩进，K&R 风格大括号，`if (...) {` 不换行

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String CACHE_KEY_PREFIX = "product:";

    /**
     * productMapper，MyBatis Mapper 数据访问层。
     */
    private final ProductMapper productMapper;

    /**
     * 分页查询。
     */
    @Override
    public PageResult<Product> findByPage(PageRequest request) {
        List<Product> list = productMapper.selectByPage(request.getOffset(), request.getPageSize());
        return PageResult.of(list, productMapper.countAll(), request);
    }

}
```

## 2. 注释与 Javadoc

- **一律中文**，且允许并鼓励写：项目是实验性质，代码价值在于解释「为什么这样写」和「代价是什么」
- 类、字段、方法都要 Javadoc；方法 Javadoc 带完整 `@param` / `@return`
- 类 Javadoc 用 `<p>`、`<ul>` 组织多段说明（设计取舍、边界、踩过的坑）
- 行内 `//` 只写取舍与坑点，**不复述代码在做什么**
- 只写看不出来的东西：并发约束、一致性前提、中间件特性、参数为什么取这个值

```java
/**
 * 删除关注关系。
 */
@Override
@Transactional(rollbackFor = Exception.class)
public void delete(Long id) {
    productMapper.deleteById(id);
    multiLevelCache.invalidateEventually(cacheKey(id));
    eventPublisher.publishEvent(new ProductChangedEvent(id));
    log.info("product deleted id={}", id);
}
```

## 3. 分层与包职责

```
com.dong.{module}/
├── controller/    # REST 接口，只做参数校验与转发
├── service/       # 只放业务接口
│   └── impl/      # 业务实现，@Transactional 在这一层
├── mapper/        # MyBatis 接口 + 枚举 TypeHandler
├── entity/        # 数据库实体（@Data）
├── dto/           # 请求 / 响应
├── enums/         # 状态枚举，落库 int
├── support/       # 无状态工具与进程内组件
├── task/          # @Scheduled 定时任务
├── handler/       # 消息处理器、事件监听
├── event/         # 进程内事件
└── listener/      # MQ 监听器
```

- **定时任务 → `task`、消息处理器 → `handler`、无状态工具 → `support`**，这三类不许进 `service`
- `framework` 是技术层，同样「接口留本包、实现进 `impl` 子包」
- 跨模块禁止反向依赖，需要联动就发进程内事件

## 4. Lombok 与注入

| 位置 | 固定写法 |
|---|---|
| entity / dto | `@Data`（字段带中文 Javadoc，不写 getter/setter） |
| 枚举 | `@Getter @AllArgsConstructor` + `private final int code` + `of(int code)` |
| service impl | `@Slf4j @Service @RequiredArgsConstructor` |
| task | `@Slf4j @Component @RequiredArgsConstructor @ConditionalOnProperty(...)` |
| controller | `@RestController @Validated @RequestMapping @RequiredArgsConstructor @Tag` |
| config | `@Configuration` + `@Value` 字段 + `@Bean(destroyMethod = "shutdown")` |

- 依赖一律**构造器注入**（`private final` 字段），不用 `@Autowired`
- 字段 Javadoc 按角色写：`xxxMapper，MyBatis Mapper 数据访问层。` / `xxxService，业务服务层。`
- 值对象（不可变 DTO、规则类、返回值）用 `record`

## 5. Controller

- 路径前缀 `/api/{module}`，接口全部返回 `Result<T>`：`Result.success(data)`、`Result.fail(code, msg)`
- 每个方法都有 `@Operation(summary = "中文")`，类上有 `@Tag(name = "中文")`
- 参数校验放 Controller：`@Valid` + `@Validated`，Service 不重复校验
- **`@NotBlank` 等价于必填**：可选字符串只能用 `@Size`（对 null 放行），且不与 `defaultValue = ""` 并存
- 数值用 `@Min` / `@Max` 封顶，上限一律引用 `Constants.MAX_*`
- 中间件开关没开时抛 `BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, ...)`，不把底层异常抛给调用方
- 私有辅助方法放类末尾，带 Javadoc

## 6. Service

- 接口只声明方法 + Javadoc，实现类每个方法都标 `@Override`
- 事务：`@Transactional(rollbackFor = Exception.class)`，写在 impl 方法上
- 业务异常：`throw new BusinessException(Constants.CODE_XXX, "message")`，消息用英文小写短句
- 错误码只取 `Constants`：`1000 参数非法 / 1001 数据不存在 / 1002 操作冲突 / 1003 限流 / 1004 中间件未启用 / 1005 依赖不可用 / 1006 幂等拒绝 / 1007 容量超限`
- 集合返回空列表不返回 `null`
- **拒绝取值越界**（负数、超长、超大批量），**允许空结果查询**（查不到就是空集，不报错）
- 全量查询必须按 `Constants.MAX_BATCH_SIZE` 之类上限截断并 `log.warn`

## 7. Mapper 与 XML

- 接口加 `@Mapper`，多参数一律 `@Param`，方法带 Javadoc
- XML：`<mapper namespace>` → `resultMap`（字段全部显式列出）→ 可选 `<sql id="columns">` 复用 → 每条语句上方 `<!-- 中文注释 -->`
- SQL 关键字**全部小写**；分页手写 `limit #{offset}, #{size}`；标量用 `resultType="java.lang.Long"`
- 主键回填：`useGeneratedKeys="true" keyProperty="id"`
- 扣减类更新把充足条件写进 `where`（`and remain_count >= #{count}`），靠影响行数判定成败，避免扣成负数

## 8. 枚举与 TypeHandler

- 状态字段用枚举，数据库存 `int`，`of(int)` 反解失败抛 `CODE_PARAM_INVALID`
- 枚举落库走 `BaseTypeHandler<T>` + `@MappedTypes(Xxx.class)`，四个方法都要有 Javadoc，`rs.wasNull()` / `cs.wasNull()` 判空
- XML 里 `javaType="com.dong.xxx.enums.XxxStatus"` 显式声明

## 9. 日志

- `@Slf4j`，占位符风格：`log.info("product updated id={}", id)`
- 关键写操作 `info`；异常降级、跳过、补偿用 `warn`；任务整体失败用 `error` 并带上异常对象
- 定时任务方法体必须 try/catch：调度线程会吞掉异常，不打日志就只能看到任务「静默地不再工作」

## 10. 配置与开关

- 业务配置放 `application.yml` 的 `dong:` 下，命名 kebab-case，注释说明默认值语义
- 读取：`@Value("${dong.xx.yy:默认值}")` 配在非 final 字段上并带 Javadoc
- 组件开关：`@ConditionalOnProperty(prefix = "dong.xx", name = "enabled", havingValue = "true")`
- 定时任务：`@Scheduled(fixedDelayString = "${dong.xx.interval-ms:600000}", initialDelayString = "${...}")`
- **改 service impl 别漏 `@ConditionalOnProperty`**：漏了关闭开关时 Bean 仍注册，会返回 1005 而不是 1004

## 11. 语言特性（JDK 21）

- `record` 用于不可变值对象；`switch` 表达式替代 if-else 链；`List.of()` / `Map.of()` / `LinkedHashMap`（需要有序时）
- 长 SQL、Lua 脚本用文本块 `"""`，常量用 `private static final`
- 数字字面量加下划线：`1_000_000L`、`20_000`
- 计数用 `LongAdder`，随机用 `ThreadLocalRandom`
- 布尔包装判空用 `Boolean.TRUE.equals(...)`；金额比较用 `compareTo` 不用 `equals`
- 判空显式写 `== null`，`Optional` 只在确有「无值」语义时用

## 12. SQL / DDL

- 关键字小写；每个字段、每张表、每个索引都带 `comment`
- 主键固定 `id bigint unsigned not null auto_increment`；唯一键 `uk_`、普通索引 `idx_`
- 时间字段：`create_time datetime default current_timestamp`、`update_time ... on update current_timestamp`
- **DDL 先行**：写 Entity / Mapper 之前先改 `db/schema.sql`，再跑 `./deploy/gen-initdb.sh`
- 已存在的库不会重跑初始化脚本，新增表要单独在远程库执行（本地无 mysql 客户端，用 Java 单文件 + JDBC）

## 13. 交付前自检

1. 每个新文件末尾有空行、注解与类型声明之间无空行
2. 方法与字段前后空行正确，方法体内部没有多余空行
3. 新增 Mapper 包已登记到 `PrimaryMybatisConfig`
4. 新增表已进 `db/schema.sql` 并重新生成 `deploy/initdb*`
5. 新增组件带 `@ConditionalOnProperty`，关闭开关时返回 1004 而不是 1005
6. 编译验证：`mvn -q clean compile`

## 参考资料

- `references/CODING_STANDARDS.md` — 逐条规则的正反例与完整示例
