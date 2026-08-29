# dong-platform 编码规范（参考文档）

## 1. 禁止任何注释

代码中绝对不能出现任何注释，包括中文注释、英文注释、JavaDoc、行内注释 `//` 等。代码应自解释。

```java
public class UserService {

    public User findByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

}
```

## 2. SQL 关键字小写

所有 SQL 关键字必须使用小写。

```sql
create table auth_user (
    id bigint primary key auto_increment,
    username varchar(32) not null unique,
    password varchar(128) not null,
    status tinyint default 1,
    create_time datetime default current_timestamp,
    update_time datetime default current_timestamp on update current_timestamp
);
```

## 3. DDL 先行

编写 Entity/Mapper 之前，必须先提供完整 `create table` 建表语句。

## 4. 方法空行

每个方法前后各有一个空行，但方法体内部不空行。

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

## 5. 实体属性空行

每个属性前面有一个空行，Getter/Setter 组前后各有一个空行。方法体内部不空行。

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

## 6. 文件末尾空行

每个 Java 文件最后一行必须是空行。

## 7. 分层架构

```
com.dong.{module}/
├── controller/    # REST 接口层
├── service/       # 业务逻辑接口
│   └── impl/      # 业务逻辑实现
├── mapper/        # MyBatis Mapper 接口
├── entity/        # 数据库实体
└── dto/           # 请求/响应 DTO
```

## 8. 命名规范

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

## 9. 统一响应体

```java
public class Result<T> {

    private Integer code;

    private String message;

    private T data;

    private Long timestamp;

    public static <T> Result<T> success(T data) { ... }

    public static <T> Result<T> error(Integer code, String message) { ... }

}
```

## 10. 异常处理

```java
throw new BusinessException(401, "Invalid credentials");
```

## 12. 请求参数校验

Controller 层使用 `@Valid` + Jakarta Validation 注解进行参数校验，Service 层不重复校验。

## 13. 分页规范

```java
public class PageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}

public class PageResult<T> {
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private List<T> list;
}
```

## 14. 配置文件分层

`bootstrap.yaml` 放 Nacos 连接信息，数据库/Redis/业务配置放 Nacos 配置中心。

## 15. Feign 调用

```java
@FeignClient(name = "dong-order", path = "/api/order")
public interface OrderClient {
}
```

## 16. 枚举统一管理

状态字段用枚举类，数据库存 `int`。

## 17. 日期时间统一

数据库 `datetime`，实体 `LocalDateTime`，JSON `yyyy-MM-dd HH:mm:ss`。

## 18. MyBatis 结果映射

默认用 `resultType`，复杂关联才用 `resultMap`。

## 19. 事务管理

```java
@Transactional(rollbackFor = Exception.class)
```

## 20. 集合返回

空结果返回 `[]`，不返回 `null`。

## 21. 常量管理

固定值统一放 `Constants` 类。

## 22. 日志规范

```java
@Slf4j
public class UserServiceImpl implements UserService {

    public User findById(Long id) {
        log.info("Finding user by id: {}", id);
        return userMapper.selectById(id);
    }

}
```
