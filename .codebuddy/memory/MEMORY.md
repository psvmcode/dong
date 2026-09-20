# 项目记忆

## dong（中间件与分布式场景实验室）

> 2026-09-06 改名：项目 `dong-lab`→`dong`，包名 `com.dong.lab.*`→`com.dong.*`，启动类 `DongApplication`，
> 配置前缀 `dong:`。**服务器目录 `/opt/dong-lab`、`deploy/lab.sh`、库名 `dong_lab`/`dong_lab_replica`、
> ES 索引前缀 `dong_lab`、环境变量 `LAB_*` 均不改**。
> 2026-08-29 由旧 HR+财务平台整体清空重建。定位：单 Spring Boot 工程，把中间件用法做成可运行、可对比、可量化的实验。

### 技术选型
- Spring Boot 3.4.5 + JDK 21 + 原生 MyBatis XML（**不用 MyBatis-Plus**）；**无前端**
- 中间件：MySQL 8 / Redis 7 / Redisson 3.45 / RocketMQ 4.9.7 / Kafka / ES 8.15.3（IK）/ MongoDB 7 / MariaDB 10.11
- 状态机：COLA StateMachine 5.0.0（order 模块）。要点：迁移被拒时**返回原状态**（需上下文加 `accepted` 标记区分）；
  内部迁移 `internalTransition().within(X).on(E)`；同 from+event 多条迁移时每条**必须**带 `when`；
  状态机管规则不管并发，落库仍需 `where status=期望 and version=当前` 乐观锁
- 分布式锁统一用 **Redisson**（`DistributedLockService`，拿不到锁返回 failed 句柄不抛异常）

### 架构与编码规范（用户明确要求）
- 单 Maven 模块；顶层按限界上下文分包，上下文内传统分层（controller / service + service/impl / mapper / entity / dto / enums）
- **写任何 Java / SQL / YAML / Vue 前必须加载 skill【dong-standards】**（SQL 关键字小写、DDL 先行、方法字段空行、文件末尾空行、Result 统一响应、状态枚举落库 int、手写 LIMIT 分页）
- **包结构**：`service` 只放接口，实现进 `service/impl`；**定时任务→`task`、消息处理器→`handler`、无状态工具→`support`**，这三类不许进 `service`；`framework` 同样「接口留本包、实现进 `impl`」
- **新增限界上下文必登记**：走主库的 Mapper 包写进 `config/PrimaryMybatisConfig` 的 `@MapperScan`，漏登记报 `No qualifying bean`
- **DDL 唯一权威源 `db/schema.sql`**，改完**必须跑 `./deploy/gen-initdb.sh`** 生成 `deploy/initdb/`（MySQL）与
  `deploy/initdb-replica/`（MariaDB）——二者是生成物，禁止手工编辑。
  ⚠️ 库里已存在的容器不会重跑初始化脚本，**新增表要单独在远程库执行**（用 Java 单文件 + JDBC）
- **注释**：`com.dong.*` 允许并鼓励中文注释（写意图/原理/坑点）；**成员顺序**：Javadoc → 注解 → 声明；
  **注解与类型声明之间不留空行**，但方法/字段前的空行必须保留
- **参数校验**：`@NotBlank` = 必填 → 可选参数只能用 `@Size`（对 null 放行），不能与 `defaultValue=""` 并存；
  **拒绝取值越界**（负数、超长、超大批量），**允许空结果查询**
- **改 service impl 别漏 `@ConditionalOnProperty`**：漏了关闭开关时 Bean 仍注册，返回 1005 而非 1004

### 模块要点
- **search（ES）**：MySQL 权威、索引可丢弃重建，单向收敛。事件只带 id 消费时回查库；afterCommit 监听器必须 try/catch；
  对账两侧全量，超限抛 1007；运维类动作结束要 `refresh()`；价格比对只能用 `compareTo`。
  索引命名：`IndexNameResolver.resolve()` 返回**别名**，真实索引带版本号，改 mapping 只能重建（建 v(n+1)→reindex→切别名→删旧）。
  自清理用 `delete_by_query`（`bool.mustNot.ids`），**不要捞全量 id 到内存**。
  ES Client 坑：补全在父类 `suggest()`；子聚合挂 `Aggregation.Builder` 的 ContainerBuilder；range 的 `ranges()` 整体替换且 from/to 是 Double；`_geo_distance` 必须显式 `.unit()`
  客户端能用 Boot 自动配置就别手写 Bean；`RestClientBuilderCustomizer` 必须实现 `customize(HttpAsyncClientBuilder)`，否则顶掉 Boot 凭据回调（401）
- **redpacket（2026-09-19 重构）**：`red_packet_item` 份额表是权威源，Redis 队列只是**副本**。
  队列元素格式 `序号:金额`；Lua 区分三种空：未预热(-2) / 真抢完(-1) / 计数与队列不一致(-2)，重复抢(-3)。
  五道防线：单用户滑动窗口限流（限流器不可用→放行，别把中间件故障放大成业务不可用）→ 本地抢完标记 → Lua 原子弹出 →
  份额 `status=0` 占位 → 扣减带充足条件。Redis 不可用时降级 DB（随机序号起步 + 乐观锁）；
  落库失败归还预扣（RESTORE 脚本与抢严格互逆）；**脏份额（占位失败）直接丢弃不归还**，否则在队列里打转。
  `RedPacketConsistencyTask` 定时对账，以库为准重建副本
- **seckill**：四道防线（限流 / 本地售罄标记 / Lua 扣减去重 / DB 唯一索引），异步 MQ 建单 + 超时回收
- **crossborder**：`doc/crossborder-payment.md`；开户默认 `singleLimit=50000`，与 AML 阈值相同，要看到人工审核分支必须显式调大

### 运行
- 单份 `src/main/resources/application.yml`，无 profile；`mvn spring-boot:run`（8090）
- 配置经 `spring.config.import: optional:file:./deploy/.env[.properties]` 自动读 `deploy/.env`
- **本地不装任何中间件**，全连云服务器；关组件：`-Dspring-boot.run.arguments="--dong.mongodb.enabled=false"`
- Knife4j `http://127.0.0.1:8090/doc.html`；验证自动配置生效看 `/actuator/beans`

### 云服务器（地址见 deploy/.env 的 LAB_PUBLIC_HOST，2核2G，CentOS 7）
- Docker Compose profile：`core / mq / kafka / search / doc / replica / full`；入口 `/opt/dong-lab/lab.sh <profile> <up|down>`
- compose 与配置统一从 `.env` 读（`${VAR:?msg}`）；生成脚本 `deploy/setup-env.sh`（服务器）/ `deploy/print-env.sh`（本地）；Mongo 密码含 `@` 要写 `%40`
- ⚠️ 安全待办（用户未执行）：轮换中间件统一密码（见 `deploy/.env`）+ 收紧安全组；
  3306/6379/9200/27017/9876/3307 对全网开放，仓库 public
- **SSH**：`ssh lab`（密钥免密）。需密码时用 `expect` 脚本，用完立即删除
- 服务器 swap 已用 1.1G：Redis 偶发 5 秒命令超时（表现为 500 + `QueryTimeoutException`），事务会回滚不脏，重试即可，**别往代码上找**

### 用户偏好
- **改完确认无误必须主动 commit + push**（中文 commit message），提交前先编译验证
- **不要自动启动项目**：改完 `mvn -q clean compile` 即可；确需端到端验证才起服务，验证完立刻关掉并确认 8090 释放
  （⚠️ 别误杀父进程是 IntelliJ IDEA 的 java）
- **本地绝不装中间件**；不清楚要二次确认；做完反复自检；汇报用简洁表格/分点

### 工具与验证的坑（反复踩过）
- **本地 Maven 仓库 `/Users/dong/.m2/dong`**，不是 `~/.m2`
- **本地无 mysql 客户端**：远程 DDL 用 `java -cp <mysql-connector-j-9.1.0.jar> /tmp/Xxx.java "<jdbc-url>" root "$PW"`
- **第三方库链式 DSL 别靠猜**，用 `javap -classpath <jar> <类>` 逐个确认签名
- **`schema.sql` 与真实库可能不一致**，加表或报"表不存在"先 `show tables`
- **改已有表注释必须 ALTER**：字段 `MODIFY COLUMN`、表 `ALTER TABLE t COMMENT=`；操作前 `mysqldump --no-data --skip-add-drop-table` 备份；
  验证 DDL 先在临时库（如 `dong_lab_chk`）跑完 drop
- **验证配置生效必须 `env -u` 清干净环境变量**（shell export 优先级高于 `.env`，假阳性）
- **端口占用会让验证失真**：先 `lsof -nP -iTCP:8090` + `pkill -9`，再 grep 日志 `Started DongApplication`；判断生效要用能区分两端的数据特征
- **跨包移动类**：补 import + 包级私有类型改 public；做法 `git mv` → sed → 由编译错误驱动
- **`delete_file` 只清空成 0 字节**，必须跟 `rm -f` 并 `ls` 验证（`git status` 显示 `M` 即没删掉）
- **`replicaSqlSessionFactory` 解析失败**：根因是 MariaDB(3307) 不可用（`new HikariDataSource` 构造即建连），处理：`mvn clean` 或 `--dong.mariadb.enabled=false`
- 宿主机 ps 能看到容器进程且显示宿主机同 uid 用户名（ES 显示 lighthouse），判归属看 `/proc/<pid>/cgroup`
- macOS 无 `timeout`；`execute_command` 不能含 sleep（会被静默杀），要等时间用 `ping -c N 127.0.0.1`
- **注入 ES 写故障**：`PUT /{index}/_settings {"index.blocks.write":true}`，测完改回 false

### 已归档
旧的多微服务 HR + 财务平台已于 2026-08-29 整体删除，勿据此改代码。
