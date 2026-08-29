# 项目记忆

## 当前项目：企业级 HR + 财务综合管理平台 (dong-platform)

> 2026-07-30 由 ERP+CRM 平台整体转型而来（ERP/CRM 服务已全部删除），前后端全量重建并验证通过。

### 技术选型
- **后端**: Spring Boot 3.4.5 + Spring Cloud 2024.0.1 + JDK 21（未启用 Nacos，直连路由）
- **数据库**: MySQL 8.0 + 原生 MyBatis（XML SQL）— **不用 MyBatis-Plus**
- **缓存**: Redis 7.x
- **认证**: 网关 JWT 校验 + auth 服务颁发（Access 2h, Refresh 7d）
- **前端**: Vue 3 + TypeScript + Vite + TDesign + Pinia
- **部署**: Docker Compose（本地 + 腾讯云 CVM YOUR_PUBLIC_HOST）

### 微服务拆分（现网）
- gateway (9000)、auth (9001, dong_auth)、hr (9002, dong_hr)、finance (9003, dong_finance)、system (9006, dong_system)
- 网关路由: /api/auth/** /api/system/** /api/hr/** /api/finance/**
- 每个服务独立 database，物理隔离

### HR 模块 (dong-hr, com.dong.hr)
- 组织(部门树/岗位)、员工(档案/入转调离异动/合同到期)、考勤(班次/排班/打卡/请假/加班/月汇总)、薪酬(结构 item_code 映射/社保方案/算薪含个税七级累进/工资条/发放锁定)、招聘(职位/候选人/面试/录用转员工)、绩效(计划/评分 S-D 等级)、培训(课程/成绩)
- 员工号: 插入后按 id 生成 E%06d；审批统一 0草稿 1审批中 2通过 3驳回
- 社保取 selectFirstActive 方案；算薪基数 21.75 天

### 财务模块 (dong-finance, com.dong.finance)
- 总账(科目树/凭证平衡校验/审核过账反过账/期末结转损益→4103/余额表)、报表(资产负债表+利润表实时算)、应收应付(发票/收付款/双向核销/账龄)、报销(明细/审批/付款生流水)、资产(直线法次月起提)、预算(执行率)、出纳(账户余额联动流水)
- 单据编号: 前缀+yyyyMM+4位序列(count+1)；凭证 JZ，状态 0草稿 1已审核 2已过账
- 科目余额按 (subject_id, period) upsert，期末余额=期初+本期发生

### 前端约定 (frontend/dong-ui)
- request.ts 拦截器解包 Result{code,message,data}，实例强转为 UnwrappedRequest 接口（get/post/put/delete 返回 Promise<T>）— 新增 API 必须走该封装
- 页面按模块合并为 tabs 大单页：hr/OrgView、EmployeeView、AttendanceView、LeaveOvertimeView、PayrollView、RecruitmentView、PerformanceView、TrainingView；finance/GLView、ReportView、ArApView、ExpenseView、AssetView、BudgetView、CashView
- API 封装: api/hr.ts、api/finance.ts（base 路径 /hr、/finance，经网关 /api 前缀）

### 开发模式：SDD（Specification-Driven Development）
1. 提问确认需求 → 2. 写 OpenAPI 规格 → 3. 用户确认 → 4. 编码

### 编码规范（Skill: dong-standards）
- **禁止中文注释**，所有注释用英文；SQL 关键字全小写；DDL 先行
- 方法/实体属性前后各空一行；controller/service/mapper 三层；分页手写 LIMIT
- 实体继承 BaseEntity(id/createTime/updateTime) + Lombok @Data @EqualsAndHashCode(callSuper=true)

### 本地运行环境（2026-07-30 搭建，已全链路验证）
- **中间件用 Homebrew 原生安装**（不用 Docker：本机无 Docker 且 GitHub/OrbStack CDN 下载极慢；Colima/OrbStack 安装残留可忽略）
- MySQL: `brew services start mysql@8.0`（8.0.46），root/root，低内存配置在 `/opt/homebrew/etc/my.cnf`（缓冲池 128M、performance_schema OFF）
- Redis: `brew services start redis`，`/opt/homebrew/etc/redis.conf` 已注释 loadmodule 行（brew 默认配置加载不存在的 redis stack 模块会导致启动失败）+ maxmemory 64mb
- 库已初始化：dong_auth/dong_system/dong_hr(23表)/dong_finance(19表) + 种子数据
- **admin 账号两处都要**：dong_system 由 DataInitializer 自动建；dong_auth 需调 `POST /api/auth/register` 注册后才能登录
- dong-system 的 SecurityConfig 必须配 SecurityFilterChain permitAll（否则 spring security 默认 basic 401；网关已做 JWT）
- 重启服务脚本化启动命令注意工作目录（nohup java -jar 用绝对或先 cd backend）

### 构建与验证（本机）
- 命令行 Maven 已装 (brew, 3.9.16)；parent pom 已配 annotationProcessorPaths(lombok) — CLI 编译必须，否则 lombok 不生效
- 旧 target/classes 可能是无 lombok 产物，遇到 getId 找不到先 mvn clean
- 验证命令: backend/dong-parent `mvn clean package -DskipTests`；frontend/dong-ui `npm run build`
- execute_command 不能含 sleep（会被静默杀掉）；长任务用 nohup 后台 + 轮询读文件

### 关键设计决策
- RBAC 权限模型；auth 只存凭证，用户详情在 system
- 登出用 Redis Token 黑名单；密码 BCrypt，5次失败锁定30分钟
- 默认账号 admin / Admin@123456


---

## 旧项目：dong (Spring Boot + Spring AI MCP Server) — 已归档
- Spring Boot 3.4.5, Java 21
- **依赖**: `spring-ai-starter-mcp-server-webmvc` (1.0.0) — SSE/WebMVC
- **协议**: SSE (`spring.ai.mcp.server.name=mcp-server`)
- **注解**: 使用 `@Tool/@ToolParam` 注解（`org.springframework.ai.tool.annotation`）
- **工具注册**: 无需手动配置 `ToolCallbackProvider`，`@Component` Bean + `@Tool` 方法会被 Spring AI MCP Server 自动发现并注册

## MCP Tool 列表（自动扫描注册）
1. **OpenMeteoService**: getAirQuality - 根据城市获取天气预报
2. **CalculatorService**: add/subtract/multiply/divide - 四则运算
3. **DateTimeService**: getCurrentDateTime/daysBetween/getWeekDay - 日期时间工具

## 注意事项
- SSE 端点: `http://localhost:8080/sse`
- 不要加 `server.servlet.context-path`
- CodeBuddy MCP 配置 URL 必须用 `http://`
- 不要使用 `MethodToolCallbackProvider` 手动注册工具（与 Spring AI 1.0.0 不兼容，会导致 `No @Tool annotated methods found` 错误）
- McpConfig 已删除，工具由 Spring AI 自动发现
