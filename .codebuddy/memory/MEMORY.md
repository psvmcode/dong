# 项目记忆

## dong（中间件与分布式场景实验室）

> 2026-08-29 由旧 HR+财务平台整体清空重建（旧微服务架构已归档，勿据此改代码）。
> 2026-09-06 改名：`dong-lab`→`dong`，包名 `com.dong.*`，启动类 `DongApplication`，配置前缀 `dong:`。
> **服务器目录 `/opt/dong-lab`、`deploy/lab.sh`、库名 `dong_lab`/`dong_lab_replica`、ES 索引前缀 `dong_lab`、环境变量 `LAB_*` 均不改。**
> 定位：单 Spring Boot 工程，把中间件用法做成可运行、可对比、可量化的实验。

### 技术选型
- Spring Boot 3.4.5 + JDK 21 + 原生 MyBatis XML（**不用 MyBatis-Plus**）
- 中间件：MySQL 8 / Redis 7 / Redisson 3.45 / RocketMQ 4.9.7 / Kafka / ES 8.15（IK）/ MongoDB 7 / MariaDB 10.11
- COLA StateMachine 5.0.0（order）：迁移被拒**返回原状态**（上下文加 `accepted` 标记区分）；同 from+event 多条迁移每条**必须**带 `when`；状态机管规则不管并发，落库仍要 `where status=期望 and version=当前`
- 分布式锁统一 **Redisson**（`DistributedLockService`，拿不到锁返回 failed 句柄不抛异常）

### 编码规范（写 Java / SQL / YAML / Vue 前**必须加载 skill【dong-standards】**）
- 单 Maven 模块；顶层按限界上下文分包，上下文内传统分层
- `service` 只放接口，实现进 `service/impl`；**定时任务→`task`、消息处理器→`handler`、无状态工具→`support`**，这三类不进 `service`；`framework` 同理「接口留本包、实现进 impl」
- 中文注释鼓励写（意图/原理/坑点）；**成员顺序 Javadoc → 注解 → 声明；注解与声明之间不留空行**；方法/字段前后空行；文件末尾空行
- 全站业务接口一律 **POST**；查询参数走 JSON body + 查询 DTO；分页 DTO 继承 `PageQuery`（改 POST 时注意与已有 POST 同路径撞车，加 `/list` 之类后缀）
- 可选参数只能用 `@Size`（`@NotBlank` = 必填）；**拒绝取值越界，允许空结果**
- 错误码只取 `Constants`：1000 参数 / 1001 不存在 / 1002 冲突 / 1003 限流 / 1004 中间件未启用 / 1005 依赖不可用 / 1006 幂等 / 1007 超限
- **改 service impl 别漏 `@ConditionalOnProperty`**：漏了关闭开关时返回 1005 而非 1004
- 新增走主库的 Mapper 包**必须登记**到 `config/PrimaryMybatisConfig` 的 `@MapperScan`

### DDL 与目录结构
- **DDL 唯一权威源 `src/main/resources/db/schema.sql`**，改完必须跑 `./src/main/resources/deploy/gen-initdb.sh` 生成 `initdb/` 与 `initdb-replica/`（生成物，禁手工编辑）
  ⚠️ 已存在的容器不会重跑初始化脚本，**新增表要单独在远程库执行**
- 顶层只有 `pom.xml`、`README.md`、`src`；`deploy`/`db`/`doc` 都在 `src/main/resources/` 下，且已被 pom 的 `<resources>` 排除出 jar
- `.gitignore` 里带路径的规则（私人面试文档、`deploy/.env`、运行日志 `/logs/`）都是**锚定式，目录再挪就失效**

### 模块要点
- **cache**：主读路径 `MultiLevelCache.resolve()` 四态 Fresh / Stale / Absent / Unavailable。**回源失败绝不写空值标记**（会把故障固化成「数据不存在」）。
  防护分层：IP 读限流 → 撞 id 扫描封禁 → L2 熔断 → 回源熔断 → 回源频控（本地实现，不依赖 Redis）→ 重建锁
- **search（ES）**：MySQL 权威、索引可丢弃重建，单向收敛。事件只带 id；afterCommit 监听器必须 try/catch；对账超限抛 1007；运维动作结束 `refresh()`；价格比对用 `compareTo`。
  索引名走 `IndexNameResolver`（**别名** + 带版本真实索引，改 mapping 只能重建）；自清理用 `delete_by_query`，别捞全量 id。
  Client 坑：补全在父类 `suggest()`；子聚合挂 ContainerBuilder；range 的 `ranges()` 整体替换且 from/to 是 Double；`_geo_distance` 必须显式 `.unit()`；
  `RestClientBuilderCustomizer` 必须实现 `customize(HttpAsyncClientBuilder)`，否则顶掉 Boot 凭据回调（401）
- **redpacket**：`red_packet_item` 份额表是权威源，Redis 队列只是**副本**（元素 `序号:金额`；Lua 区分未预热/真抢完/计数不一致/重复抢）。
  五道防线：滑动窗口限流 → 本地抢完标记 → Lua 原子弹出 → `status=0` 占位 → 扣减带充足条件；Redis 不可用时降级 DB（乐观锁）；
  落库失败归还预扣，**脏份额直接丢弃不归还**；`RedPacketConsistencyTask` 以库为准重建副本
- **seckill**：四道防线（限流 / 本地售罄标记 / Lua 扣减去重 / DB 唯一索引），异步 MQ 建单 + 超时回收
- **crossborder**：开户默认 `singleLimit=50000` 与 AML 阈值相同，要看到人工审核分支必须显式调大
- **agent**（`doc/scenario/agent.md`，页面 `static/agent/index.html`）：把 LLM 应用的工程问题做成对照实验（E1~E8）。
  零新增 Maven 依赖（JDK HttpClient + SseEmitter）；工具进 `support/tool` 且**直调 service 不回打自己的接口**；五道停止闸门 + 必有 `finish_reason`；默认 `mock` provider。
  最大风险：**SSE 长期占用 tomcat 线程（max 200）** → 并发上限 + 墙钟超时 + cleanup。页面禁用 `EventSource`（仅 GET），用 `fetch` + `ReadableStream`
- **upload**：分片上传 + 断点续传 + 秒传；表 `upload_task`/`upload_chunk`。**uploadId 由内容确定性推导**（hash+size+name+chunkSize 的 MD5）。
  `dong.upload.persist-data` 默认 false（只记元信息）。坑：tomcat `max-http-form-post-size` 默认 2MB 会让 5MB 分片 413 且不进 Controller，已放开到 100MB。
  铁律：写片 `Files.copy`、合并 `transferTo`，**绝不 readAllBytes**；路径安全：`safeName` 排除 `.`/`..`，删除前校验 `startsWith(存储根绝对路径)`；收分片必须 `touch` 任务防过期清理
- **logging（2026-10-27 新增，ELK）**：见下节

### logging 模块（ELK 日志检索，2026-10-27）
`com.dong.logging`，接口 `/api/logs`，页面 `static/logs/index.html`，文档 `doc/scenario/logging.md`。三件事合一：
- **采集**：`ElasticsearchLogAppender`（logback，用 `UnsynchronizedAppenderBase`）→ `LogEventBuffer`（有界 1 万，80% 水位后只留 WARN+）→ `LogShipperTask` 定时批量写 ES。
  硬要求：打日志的线程只入队（同步写 ES 会拖慢业务且自激递归）；按 logger 前缀挡掉 `com.dong.logging`/`org.elasticsearch`/`co.elastic.clients`/`org.apache.http`；
  appender 方法体必须整体 try/catch；**缓冲容量只能配在 `logback-spring.xml`**（appender 早于 Spring 容器）
- **补齐**：`LogFileImportService` 解析落盘文件回填，**位点按行数记并存在 ES**（字节偏移会漂移），只导主日志与其轮转 `.N.log.gz`，按日志时间归档到各自那天的索引，超保留窗口的轮转目录整体跳过
- **存储**：按天索引 `dong_lab_log_yyyy_MM_dd` + 读别名 `dong_lab_log`，过期**整块删索引**（不是 delete_by_query）；保留天数 14，与 logback `maxHistory` 对齐；**无写别名**（写入目标由日期算出）
- **检索/可视化**：检索、上下文、仪表盘共用一套过滤条件；索引未建时返回空而非报错；`LogTraceFilter` 注入 MDC traceId 并回写 `X-Trace-Id`（必须在 finally 清理 MDC）
- 实测：启动 20 秒采集 2438 条零丢弃；关闭开关返回 1004

### rpc 模块（RPC 框架，2026-10-27）
`com.dong.rpc`，接口 `/api/rpc`，文档 `doc/scenario/rpc.md`。跨进程真 RPC：服务端监听独立端口 9090，客户端走真实 TCP，进程内直调同一实现作对照。
- **协议零依赖自研**：varint / zigzag（整型统一走 zigzag，等价官方 sint32/sint64，**不与官方 int32 互通**）/ TLV / 嵌套 / repeated / fixed 小端；
  schema 用 `@ProtoMessage` + `@ProtoField(order, type, repeated)`，首次使用时反射并缓存未知字段按线类型跳过（前向兼容）
- **传输 Netty**：帧 = 4 字节长度 + protobuf 报文（LengthField 解决粘包拆包）；单地址长连接复用 + requestId 挂起实现并发；
  必须有超时与最大帧长上限；绑定失败只记 error 不阻断启动
- **注册发现 Redis ZSet**：score = 心跳时间戳，TTL 续约 + 定时清区间（解决被强杀实例残留）；
  客户端本地缓存服务列表但**空结果不缓存**；Redis 不可用时降级到 `dong.rpc.default-addresses`
- **依赖方向**：接口与注解在 rpc 模块，业务模块写实现并打 `@RpcService`（示例：`ProductQueryService` 实现在 search 模块，按 ES 开关启停）；rpc 不依赖任何业务模块
- **实测教训**：首次调用 33ms 里几乎全是注册中心往返（云服务器 Redis RTT ~30ms），加本地缓存后降到 0.29ms/次；
  同一份数据 protobuf 110B vs JSON 233B。做性能对照前先确认「寻址」有没有被算进去

### 前端项目 dong-web
- `/Users/dong/items/java/dong-web`（同级独立仓库，**有远端** `git@github.com:psvmcode/dong-web.git`，master 可 push）；Vue 3 + TS + Vite 6 + Element Plus + Pinia + ECharts
- **不改后端任何配置**：vite 代理 `/api` `/actuator` `/v3` → 8090；`DONG_BACKEND_TARGET=http://x:8090 npm run dev`
- `postQuery` 对应 @RequestParam，`postJson` 对应 @RequestBody；`unwrap()` 把失败也包成 ApiResult（页面要看得到 1004/1005）
- `npm install` 必须加 `--include=dev`；交付前跑 `npm run build`（内含 vue-tsc 类型检查）
- **页面形态（2026-11-10 用户明确改口，取代旧要求）**：不要太花里胡哨、一定要实用。统一白底 + 细边框 + 6px 圆角 + 单一主色 `#2f6feb`，颜色只承担语义；禁止渐变、大色块、拟物化外壳、emoji、悬浮位移与动画；数字等宽右对齐
  - 旧要求「还原真实产品界面、各场景用自己的配色」**已作废**，别再按它做
  - 保留：调试信息仍折叠进右下角「内部链路」抽屉；真实动作感（抢购/拆红包/补全分面）不退回参数表单
  - 设计基线见 `src/styles/global.css`；全局 `CopyButton` 在 `main.ts` 注册；`utils/scene.ts` 已停用彩色头像与分类渐变

### 运行与部署
- 单份 `src/main/resources/application.yml`，无 profile；`mvn spring-boot:run`（8090）；Knife4j `/doc.html`
- 配置经 `spring.config.import: optional:file:./src/main/resources/deploy/.env[.properties]` 自动读取
- **本地不装任何中间件**，全连云服务器；关组件：`-Dspring-boot.run.arguments="--dong.mongodb.enabled=false"`
- 云服务器（地址见 `.env` 的 LAB_PUBLIC_HOST，2核2G，CentOS 7）：`ssh lab`；`/opt/dong-lab/lab.sh <profile> <up|down>`，profile `core/mq/kafka/search/doc/replica/full`
- ⚠️ 安全待办（用户未执行）：轮换中间件统一密码 + 收紧安全组（3306/6379/9200/27017/9876/3307 对全网开放，仓库 public）
- 服务器 swap 已用 1.1G：Redis 偶发 5 秒命令超时（500 + `QueryTimeoutException`），事务会回滚不脏，重试即可，**别往代码上找**

### 用户偏好
- **改完确认无误必须主动 commit + push**（中文 commit message），提交前先编译验证
- **不要自动启动项目**：改完 `mvn -q clean compile` 即可；确需端到端验证才起服务，验证完立刻关掉并确认 8090 释放（⚠️ 别误杀父进程是 IntelliJ IDEA 的 java）
- **本地绝不装中间件**；不清楚要二次确认；做完反复自检；汇报用简洁表格/分点

### 工具与验证的坑（反复踩过）
- **本地 Maven 仓库 `/Users/dong/.m2/dong`**，不是 `~/.m2`
- **第三方库链式 DSL 别靠猜**，用 `javap -classpath <jar> <类>` 逐个确认签名
- **本地无 mysql 客户端**：远程 DDL 用 `java -cp <mysql-connector-j-9.1.0.jar> /tmp/Xxx.java "<jdbc-url>" root "$PW"`
- **`schema.sql` 与真实库可能不一致**，加表或报"表不存在"先 `show tables`；改表注释必须 ALTER（先 `mysqldump --no-data` 备份），验证 DDL 先在临时库（如 `dong_lab_chk`）跑完 drop
- **验证配置生效必须 `env -u` 清干净环境变量**；**端口占用会让验证失真**：先 `lsof -nP -iTCP:8090` + kill，再 grep 日志 `Started DongApplication`
- **跨包移动类**：补 import + 包级私有类型改 public；做法 `git mv` → sed → 由编译错误驱动
- **`delete_file` 只清空成 0 字节**，必须跟 `rm -f` 并 `ls` 验证
- **`replicaSqlSessionFactory` 解析失败**：根因 MariaDB(3307) 不可用，处理 `mvn clean` 或 `--dong.mariadb.enabled=false`
- 宿主机 ps 能看到容器进程（ES 显示 lighthouse），判归属看 `/proc/<pid>/cgroup`
- macOS 无 `timeout`；`execute_command` 不能含 sleep（会被静默杀），要等时间用 `ping -c N 127.0.0.1`
- **注入 ES 写故障**：`PUT /{index}/_settings {"index.blocks.write":true}`，测完改回 false
