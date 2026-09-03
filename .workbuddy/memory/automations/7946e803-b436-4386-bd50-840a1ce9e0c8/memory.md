# 自动化任务执行记录：跨境支付场景持续完善

## 任务内容
对 dong-lab 跨境支付（crossborder）模块持续完善：代码质量与中文注释、新增业务场景、数据落库、README 文档、编译检查、中文 commit 提交。

## 2026-09-03 执行（第 1 次记录）

- 已有基础：此前多轮提交已完成八大环节主链路（幂等、合规、锁汇、扣款、清算、入账、对账）与大部分中文注释。
- 本次新增两个场景闭环：
  1. 人工审核闭环：`POST /remittance/{no}/review/approve|reject`、`GET /remittance/pending-review`；ComplianceCheckType 加 MANUAL_REVIEW(5)；放行走乐观锁抢占 PENDING_REVIEW→QUOTE_LOCKED，中途失败回退可重审；驳回释放日限额。实现位于 RemittanceServiceImpl.approveReview/rejectReview + CrossBorderLedgerService.debitExisting + updateSettlementTerms。
  2. 账户冻结/解冻：新表 cross_border_account_event（DDL 在 deploy/initdb/01-schema.sql 末尾），接口 `POST /accounts/{no}/freeze|unfreeze`、`GET /accounts/{no}/events`；枚举 AccountStatus/AccountEventType + TypeHandler 注册。
- 修复 bug：doCreate 合规直接拒绝分支未释放日限额占用（额度泄漏）。
- 注释补齐：7 个响应 DTO 类注释、8 个实体字段级注释、8 个 Mapper XML 注释头。
- README 5.11 节重构为三部分：业务场景讲解 / 接口文档讲解（45 接口分组表）/ 技术场景讲解；接口总数 88→141。
- 编译通过（mvn compile），提交 3bcfd78，46 文件 +899/-11。
- 注意：新表 cross_border_account_event 需在服务器重新执行 initdb 或手工建表后生效。

## 后续可做
- 汇款单轨迹查询（按状态变更时间线重建）。
- 收款人白名单场景。
- 审核超时自动提醒/升级。
- deploy/initdb 与 db/schema.sql 的口径统一（db/schema.sql 无 crossborder 表）。
