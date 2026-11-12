# 项目记忆

## dong（中间件与分布式场景实验室）

> 2026-08-29 由旧 HR+财务平台清空重建；2026-09-06 改名 `dong-lab`→`dong`，包名 `com.dong.*`，启动类 `DongApplication`，配置前缀 `dong:`。
> **服务器目录 `/opt/dong-lab`、`deploy/lab.sh`、库名 `dong_lab`/`dong_lab_replica`、ES 前缀 `dong_lab`、环境变量 `LAB_*` 均不改。**
> 单 Spring Boot 工程，把中间件用法做成可运行、可对比、可量化的实验。

### 技术与编码（写 Java/SQL/YAML/Vue 前**必须加载 skill【dong-standards】**）
- Spring Boot 3.4.5 + JDK 21 + 原生 MyBatis XML（**不用 MyBatis-Plus**）；MySQL8 / Redis7 / Redisson3.45 / RocketMQ4.9.7 / Kafka / ES8.15(IK) / MongoDB7 / MariaDB10.11
- 单 Maven 模块，按限界上下文分包；`service` 只放接口（实现进 `impl`），定时任务→`task`、消息处理器→`handler`、无状态工具→`support`，`framework` 同理
- 全站业务接口**一律 POST**，查询参数走 JSON body + 查询 DTO，分页继承 `PageQuery`；同路径动作加 `/delete`、`/update` 等后缀（2026-11-11 已全量收敛，dong-web 同步改 `postQuery`/`postJson`）
- 可选参数只用 `@Size`（`@NotBlank`=必填）；**拒绝取值越界，允许空结果**；集合返回空列表不返回 null；全量查询按 `Constants.MAX_*` 截断
- 错误码只取 `Constants`：1000 参数 / 1001 不存在 / 1002 冲突 / 1003 限流 / 1004 中间件未启用 / 1005 依赖不可用 / 1006 幂等 / 1007 超限
- COLA StateMachine（order）：迁移被拒返回原状态 + `accepted` 标记；同 from+event 多条迁移每条必须带 `when`；落库仍要 `where status=期望 and version=当前`
- 分布式锁统一 Redisson `DistributedLockService`（拿不到锁返回 failed 句柄不抛异常）
- 新增主库 Mapper 包**必须登记** `config/PrimaryMybatisConfig` 的 `@MapperScan`
- `@ConditionalOnProperty` 不能机械加：只有 Controller 用 `ObjectProvider` 延迟获取（search/logging）才适合给 impl 加；agent/upload 是 `private final` 直接注入 + `@Value` 显式抛 1004，加注解会 Bean 缺失、启动失败
- 中文注释鼓励写（意图/原理/坑点）；Javadoc→注解→声明、注解与声明间不留空行、方法/字段前后空行、文件末尾空行；Lombok 固定用法见 skill

### DDL 与目录
- **DDL 唯一权威源 `src/main/resources/db/schema.sql`**，改完跑 `./src/main/resources/deploy/gen-initdb.sh` 生成 `initdb/`、`initdb-replica/`（生成物禁手改）；已存在容器不会重跑初始化，新增表要单独在远程库执行
- 顶层只有 `pom.xml`、`README.md`、`src`；`deploy`/`db`/`doc` 在 `src/main/resources/` 下且被 pom `<resources>` 排除出 jar
- `.gitignore` 带路径规则（私人文档、`deploy/.env`、`/logs/`）是锚定式，目录挪动即失效

### 模块要点
- **cache**：`MultiLevelCache.resolve()` 四态 Fresh/Stale/Absent/Unavailable；**回源失败绝不写空值标记**。防护：IP 读限流→撞 id 封禁→L2 熔断→回源熔断→回源频控→重建锁
- **search(ES)**：MySQL 权威、索引可丢弃重建、单向收敛；事件只带 id；afterCommit 监听必须 try/catch；对账超限抛 1007；运维动作结束 `refresh()`；价格用 `compareTo`；索引走 `IndexNameResolver`（别名+版本真实索引）；自清理用 `delete_by_query`
  Client 坑：补全在父类 `suggest()`、子聚合挂 ContainerBuilder、range 的 `ranges()` 整体替换且 from/to 是 Double、`_geo_distance` 必须 `.unit()`、`RestClientBuilderCustomizer` 必须实现 `customize(HttpAsyncClientBuilder)` 否则 401
- **redpacket**：`red_packet_item` 是权威源，Redis 队列只是副本（元素 `序号:金额`）。五道防线：滑动窗口限流→本地抢完标记→Lua 原子弹出→`status=0` 占位→带充足条件扣减；Redis 不可用降级 DB 乐观锁；落库失败归还预扣、**脏份额直接丢弃**；`RedPacketConsistencyTask` 以库为准重建副本
- **seckill**：限流 / 本地售罄标记 / Lua 扣减去重 / DB 唯一索引 + 异步 MQ 建单与超时回收
- **agent**：E1~E8 对照实验；零新增依赖（JDK HttpClient + SseEmitter）；工具进 `support/tool` 直调 service；五道停止闸门 + 必有 `finish_reason`；默认 mock。**最大风险 SSE 长期占 tomcat 线程(max 200)** → 并发上限+墙钟超时+cleanup；页面禁用 `EventSource`，用 `fetch`+`ReadableStream`
- **upload**：分片/断点续传/秒传，表 `upload_task`/`upload_chunk`；uploadId 由 hash+size+name+chunkSize 的 MD5 确定性推导；`dong.upload.persist-data` 默认 false；form-post 已放开 100MB；铁律：写片 `Files.copy`、合并 `transferTo`、**绝不 readAllBytes**；`safeName` 排除 `.`/`..`，删除前校验 `startsWith(根绝对路径)`；收分片必须 `touch`
- **logging(ELK)** `com.dong.logging` `/api/logs`：logback `UnsynchronizedAppenderBase`→有界 1 万缓冲(80% 水位后只留 WARN+)→定时批量写 ES；打日志线程只入队；按 logger 前缀挡 `com.dong.logging`/`org.elasticsearch`/`co.elastic.clients`/`org.apache.http`；appender 整体 try/catch；**缓冲容量只能配在 logback-spring.xml**。补齐按**行数**记位点存 ES，只导主日志与 `.N.log.gz`。按天索引 `dong_lab_log_yyyy_MM_dd` + 读别名 `dong_lab_log`、过期整块删索引、保留 14 天、**无写别名**；索引未建返回空；`LogTraceFilter` 必须 finally 清 MDC
- **rpc** `com.dong.rpc` `/api/rpc`：9090 独立端口、真实 TCP、进程内直调对照。自研协议 varint/zigzag(等价官方 sint32/64，**不与官方 int32 互通**)/TLV/repeated/fixed 小端；`@ProtoMessage`+`@ProtoField`，反射缓存 + 未知字段按线类型跳过；Netty 4 字节长度帧 + requestId 挂起 + 超时/最大帧长；绑定失败只记 error。注册发现 Redis ZSet(score=心跳时间戳，TTL 续约+定时清区间)，本地缓存服务列表但**空结果不缓存**，Redis 挂了降级 `dong.rpc.default-addresses`；rpc 不依赖业务模块。教训：首调 33ms 全是注册中心 RTT，缓存后 0.29ms；protobuf 110B vs JSON 233B
- **crossborder**：开户默认 `singleLimit=50000` 等于 AML 阈值，要看人工审核需显式调大

### 前端 dong-web
- `/Users/dong/items/java/dong-web`（独立仓库，远端 `git@github.com:psvmcode/dong-web.git`）；Vue3+TS+Vite6+Element Plus+Pinia+ECharts
- 不改后端配置；vite 代理 `/api` `/actuator` `/v3`→8090；`postQuery`=@RequestParam、`postJson`=@RequestBody；`unwrap()` 把失败也包成 ApiResult 让页面看到 1004/1005
- `npm install --include=dev`；交付前 `npm run build`
- **页面形态（2026-11-10 改口，取代旧要求）**：实用优先，白底+细边框+6px 圆角+单一主色 `#2f6feb`，颜色只承担语义；禁渐变/大色块/拟物外壳/emoji/悬浮位移动画；数字等宽右对齐；调试信息折叠进「内部链路」抽屉；保留真实动作感。基线 `src/styles/global.css`

### 运行与部署
- 单份 `src/main/resources/application.yml` 无 profile；`mvn spring-boot:run`（8090）；Knife4j `/doc.html`；配置经 `spring.config.import: optional:file:./src/main/resources/deploy/.env[.properties]`
- **本地不装任何中间件**全连云服务器；关组件用 `-Dspring-boot.run.arguments="--dong.mongodb.enabled=false"`
- 云服务器 `ssh lab`（2核2G CentOS7）：`/opt/dong-lab/lab.sh <core|mq|kafka|search|doc|replica|full> <up|down>`
- ⚠️ 待办：轮换中间件密码 + 收紧安全组（3306/6379/9200/27017/9876/3307 全网开放，仓库 public）
- 服务器 swap 1.1G：Redis 偶发 5 秒超时（500 + `QueryTimeoutException`），事务回滚不脏，重试即可，**别往代码上找**

### 用户偏好
- **改完确认无误主动 commit + push，commit message 必须中文（英文硬禁止）**，提交前先编译验证
- **不要自动启动项目**：`mvn -q clean compile` 即可；确需端到端验证才起服务，验证完立刻关掉并确认 8090 释放（⚠️ 别误杀父进程是 IntelliJ IDEA 的 java）
- 本地绝不装中间件；不清楚要二次确认；做完反复自检；汇报用简洁表格/分点

### 工具与验证的坑
- 本地 Maven 仓库 `/Users/dong/.m2/dong`；第三方库链式 DSL 用 `javap -classpath <jar> <类>` 确认签名，别猜
- 本地无 mysql 客户端：远程 DDL 用 `java -cp <mysql-connector-j-9.1.0.jar> /tmp/Xxx.java "<jdbc-url>" root "$PW"`
- `schema.sql` 与真实库可能不一致，报"表不存在"先 `show tables`；改表注释用 ALTER（先 `mysqldump --no-data` 备份），验证 DDL 先在临时库 `dong_lab_chk` 跑完 drop
- 验证配置生效必须 `env -u` 清环境变量；端口占用会让验证失真：`lsof -nP -iTCP:8090` + kill，再 grep `Started DongApplication`
- 跨包移动类：`git mv`→sed→编译错误驱动，补 import + 包级私有改 public
- **`delete_file` 只清空成 0 字节**，必须跟 `rm -f` 并 `ls` 验证
- `replicaSqlSessionFactory` 解析失败根因是 MariaDB(3307) 不可用：`mvn clean` 或 `--dong.mariadb.enabled=false`
- 宿主机 ps 能看容器进程，判归属看 `/proc/<pid>/cgroup`；macOS 无 `timeout`；`execute_command` 不能含 sleep，用 `ping -c N 127.0.0.1` 等待
- 注入 ES 写故障：`PUT /{index}/_settings {"index.blocks.write":true}`，测完改回 false
