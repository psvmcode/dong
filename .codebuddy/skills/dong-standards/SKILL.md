---
name: dong-standards
description: 本技能应在为 dong-platform 项目（ERP + CRM）编写任何代码时使用。强制执行严格的编码规范：禁止中文注释、SQL 关键字小写、DDL 先行建表、方法/字段空行规则、传统分层架构。在项目中生成任何 Java、SQL、YAML 或 Vue 代码之前，始终加载此技能。
---

# dong-platform 编码规范

此技能强制执行 dong-platform ERP + CRM 系统的项目编码规范。

## 何时使用

在 `/Users/dong/items/java/dong` 项目中编写任何代码时，**始终**加载此技能。适用于：
- Java 文件（Controller、Service、Mapper、Entity、DTO、Config）
- SQL 文件（DDL、DML）
- YAML 文件（application.yaml、bootstrap.yaml、OpenAPI 规格）
- Vue/TypeScript 文件（前端代码）

## 核心规则

### 1. 禁止任何注释

代码中绝对不能出现任何注释，包括中文注释、英文注释、JavaDoc、行内注释 `//` 等。代码应自解释。

### 2. SQL 关键字小写

所有 SQL 关键字必须使用小写：`create table`、`varchar`、`int`、`primary key`、`not null`、`default`、`auto_increment`、`select`、`from`、`where`、`insert`、`update`、`delete`、`limit`、`order by` 等。

### 3. DDL 先行建表

在编写任何 Entity 或 Mapper 之前，必须先提供完整的 DDL `create table` 建表语句。每个表字段必须显式定义。

### 4. 方法空行

每个方法前后各有一个空行，但方法体内部不空行：

```java
public class UserService {

    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    public List<User> findAll() {
        return userMapper.selectAll();
    }

}
```

### 5. 实体属性空行

实体类中，每个属性前面有一个空行，Getter/Setter 方法组前后各有一个空行：

```java
public class User {

    private Long id;

    private String username;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
```

### 6. 文件末尾空行

每个 Java 文件最后一行必须是空行。

### 7. 传统分层架构

```
com.dong.{module}/
├── controller/    # REST 接口层
├── service/       # 业务逻辑接口
│   └── impl/      # 业务逻辑实现
├── mapper/        # MyBatis Mapper 接口
├── entity/        # 数据库实体
└── dto/           # 请求/响应 DTO
```

### 8. 命名规范

| 类型 | 命名模式 | 示例 |
|------|----------|------|
| Controller | `{Entity}Controller` | `UserController` |
| Service 接口 | `{Entity}Service` | `UserService` |
| Service 实现 | `{Entity}ServiceImpl` | `UserServiceImpl` |
| Mapper 接口 | `{Entity}Mapper` | `UserMapper` |
| Mapper XML | `{Entity}Mapper.xml` | `UserMapper.xml` |
| 实体类 | `{Entity}` | `User` |
| 请求 DTO | `{Action}{Entity}Request` | `LoginRequest` |
| 响应 DTO | `{Entity}Response` | `UserResponse` |

### 9. 统一响应体

所有 API 响应必须使用 `Result<T>` 包装：

```java
Result<UserResponse> result = Result.success(userResponse);
```

### 10. 异常处理

业务异常必须使用 `BusinessException`：

```java
throw new BusinessException(401, "Invalid credentials");
```

### 11. 日志规范

使用 Lombok 的 `@Slf4j` 注解，关键操作记录 info 级别日志：

```java
@Slf4j
public class UserServiceImpl implements UserService {

    public User findById(Long id) {
        log.info("Finding user by id: {}", id);
        return userMapper.selectById(id);
    }

}
```

## SDD 开发流程（规格驱动开发）

编写任何服务代码之前，按以下顺序执行：

1. 向用户提问确认需求
2. 编写 OpenAPI 规格文档到 `docs/api/specs/{service}-api.yaml`
3. 请用户确认规格
4. 编写 DDL `create table` 建表语句
5. 生成代码骨架（Entity → Mapper → Service → Controller）
6. 实现业务逻辑

### 12. 请求参数校验

Controller 层使用 `@Valid` + Jakarta Validation 注解进行参数校验，Service 层不重复校验。

### 13. 分页规范

分页请求统一用 `PageRequest`（pageNum, pageSize），响应用 `PageResult<T>`（total, list）。Mapper XML 手写 `limit`。

### 14. 配置文件分层

`bootstrap.yaml` 放 Nacos 连接信息，数据库/Redis/业务配置放 Nacos 配置中心。

### 15. Feign 调用

Feign 接口定义在调用方 `client/` 包下，不跨服务复用。

### 16. 枚举统一管理

状态字段用枚举类，数据库存 `int`，避免魔法数字。

### 17. 日期时间统一

数据库 `datetime`，实体 `LocalDateTime`，JSON 序列化 `yyyy-MM-dd HH:mm:ss`。

### 18. MyBatis 结果映射

默认用 `resultType`，复杂关联才用 `resultMap`。

### 19. 事务管理

`@Transactional(rollbackFor = Exception.class)` 明确指定回滚策略。

### 20. 集合返回

空结果返回 `[]`，不返回 `null`。

### 21. 常量管理

固定值统一放 `Constants` 类。

## 参考资料

完整的编码规范详见：
- `references/CODING_STANDARDS.md` — 完整编码规范参考文档
- 项目文档：`docs/architecture/DESIGN.md`、`docs/architecture/STEPS.md`
