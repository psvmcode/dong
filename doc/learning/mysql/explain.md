# 执行计划：怎么看出一条 SQL 慢在哪

> 目标：会看 explain 的关键列，知道哪些写法会让索引失效。

---

## 一句话

**explain 就是让 MySQL 把它的执行思路告诉你——打算用哪个索引、要扫多少行、要不要回表。**

用法极简单，在 SQL 前面加 `explain`：

```sql
explain select * from user where name = '张三';
```

---

## 最该关注的三列

explain 输出十几列，日常优化盯这三个就够了：**type**、**key**、**Extra**。

### 1. type：访问类型，性能高低的直接体现

从好到坏（**背下来，面试必考**）：

```
system > const > eq_ref > ref > range > index > ALL
```

| 值 | 含义 | 什么时候出现 |
|---|---|---|
| `system` | 表里只有一行 | 几乎见不到 |
| `const` | 主键或唯一索引等值查，最多命中一行 | `where id = 1` |
| `eq_ref` | join 时，被驱动表用主键/唯一索引关联 | join 的理想情况 |
| `ref` | 普通索引等值查，可能命中多行 | `where name = '张三'` |
| `range` | 范围查询 | `where id > 100`、`between`、`in` |
| `index` | **扫全索引**（比全表稍好，因为索引小） | 没走 where，但要扫整个索引 |
| `ALL` | **全表扫描** | 没用上索引 |

**优化标准**：
- 至少要到 `range`
- 最好到 `ref`
- **出现 `ALL` 或 `index` 且数据量大，基本要优化**
- 有个经验说法：ALL 和 index 是「及格线以下」，range 及格，ref 良好，const/eq_ref 优秀

### 2. key：实际用到的索引

- `key` 有值 → 用上索引了
- `key` 是 `NULL` → **没用索引**，要查原因

同时看 `possible_keys`（理论上可能用的索引）和 `key`（实际用的）。
如果 possible_keys 有值但 key 是 NULL，说明**优化器算下来觉得全表更划算**（比如命中行数太多）。

`rows` 列是**预计要扫描的行数**，这个数字越小越好。

### 3. Extra：额外信息，藏着关键线索

| 值 | 含义 | 好坏 |
|---|---|---|
| `Using index` | **覆盖索引**，不用回表 | ✅ 很好 |
| `Using index condition` | 用了索引下推（ICP） | ✅ 好 |
| `Using where` | 在存储引擎返回后，server 层又过滤了一遍 | ⚠️ 正常，但可优化 |
| `Using temporary` | 用了**临时表**（group by、distinct 常见） | ❌ 要优化 |
| `Using filesort` | 用了**外部排序**（order by 没走索引） | ❌ 要优化 |
| `Using join buffer` | join 没走索引，用了 join buffer | ❌ 要优化 |

**后三个是红灯**，数据量大时会明显拖慢查询。

---

## 索引失效的九种情况

这是面试最高频的问题，逐个说：

### 1. 违反最左前缀

```sql
-- 有联合索引 (a, b, c)
where b = 2                    -- ❌ 跳过了 a
where a = 1 and c = 3          -- ⚠️ 只用到 a
```

### 2. 在索引列上用函数

```sql
where year(create_time) = 2026        -- ❌
where create_time >= '2026-01-01'     -- ✅ 改成范围
```

### 3. 隐式类型转换

```sql
-- phone 是 varchar
where phone = 13800138000      -- ❌ 数字，触发隐式转换
where phone = '13800138000'    -- ✅
```

**反过来也要小心**：字段是 int，你传字符串 `'123'`，MySQL 会把字符串转成数字，
**这时索引仍然有效**（因为转换发生在常量侧，不在列上）。
规则是：**类型转换发生在列上才失效**。

### 4. 使用 != 或 not in

```sql
where status != 1              -- ❌ 通常不走索引
where status in (2, 3)         -- ✅ 改写成 in
```

### 5. like 以 % 开头

```sql
where name like '%三'          -- ❌ 前缀不确定，没法用索引定位
where name like '张%'          -- ✅ 前缀确定，能用索引
```

### 6. or 连接的条件里有非索引列

```sql
-- name 有索引，age 没索引
where name = '张三' or age = 20    -- ❌ 因为 age 没索引，整个条件都得全表扫
```

解决：给 age 也加索引，或拆成两个查询用 union。

### 7. 范围查询右边的列失效

```sql
-- 有联合索引 (a, b, c)
where a = 1 and b > 10 and c = 3   -- ⚠️ c 用不上（b 是范围）
```

建索引时把范围查询的列放最后。

### 8. 对索引列做运算

```sql
where id + 1 = 10              -- ❌
where id = 9                   -- ✅ 把运算挪到常量侧
```

### 9. 优化器判定全表更快

命中行数超过全表的一定比例（经验值约 20%~30%）时，
优化器会认为「反正要回表那么多次，不如直接全表扫」。

这不是 bug，是优化器的成本计算。如果判断错了，可以用 `force index` 强制走索引，
但**优先相信优化器**，它算错的概率比人小。

---

## 排查慢 SQL 的标准流程

```sql
-- 1. 先看执行计划
explain select ...;

-- 2. 关注三件事
--    type 是不是 ALL
--    key 是不是 NULL
--    rows 是不是特别大

-- 3. 如果 Extra 有 Using filesort / Using temporary，优先优化排序和分组

-- 4. 想看真实执行数据（不只是预估），用 explain analyze（MySQL 8.0.18+）
explain analyze select ...;
```

`explain` 给的是**预估**（基于统计信息），`explain analyze` 会**真的执行一遍**并给出实际耗时，
8.0.18 之后排查慢 SQL 用它更准。

---

## 一个优化实例

```sql
-- 慢查询：Extra 出现 Using filesort，type 是 ALL
explain select name, age from user where city = '北京' order by age;
```

分析：
1. `city` 没索引 → 全表扫描（type=ALL）
2. `order by age` 没索引 → 文件排序（Using filesort）

优化：建联合索引 `(city, age)`

```sql
create index idx_city_age on user(city, age);
```

效果：
1. `city = '北京'` 走索引 → type 到 ref
2. 索引里 age 已经有序 → **排序省了**，Using filesort 消失
3. 查询列 name、age 里 age 在索引中，但 name 不在 → 仍有回表

如果再进一步，把 name 也加进索引 `(city, age, name)`，就是覆盖索引，Extra 出现 `Using index`。

---

## 记忆口诀

| 看什么 | 看什么内容 |
|---|---|
| type | 别出现 ALL / index，至少 range，最好 ref |
| key | 别是 NULL，NULL 就是没走索引 |
| rows | 越小越好，是预估扫描行数 |
| Extra | `Using index` 是好事；`Using filesort`、`Using temporary` 是红灯 |

**索引失效两大类原因**：
- 写法问题（函数、类型转换、`%` 开头、`!=`、运算、违反最左前缀）
- 优化器判断（命中太多行，觉得全表更便宜）
