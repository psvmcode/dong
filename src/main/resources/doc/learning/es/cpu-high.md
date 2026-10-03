# ES CPU 打满：原因、排查与救火

> 目标：说清什么在烧 CPU、三条命令定位、以及**最快止血的几种手段**。

---

## 一句话

**ES 的 CPU 打满，九成是「查询」或「merge」两类在烧——
先用 `_nodes/hot_threads` 一眼看出是谁，再决定是杀查询还是停写入。**

---

## 一、先分清：是哪个进程在烧

ES 是 Java 进程，CPU 高有两种性质完全不同的情况：

| 类型 | 表现 | 处理方向 |
|---|---|---|
| **ES 自己算得凶** | 线程堆栈里全是 Lucene / search 相关 | 优化查询、杀查询 |
| **GC 在烧 CPU** | GC 线程占比高，堆内存反复涨落 | 查内存、扩容、重启 |

**判断方法**：看 CPU 高的时候堆内存是不是也在剧烈波动。
如果 GC 频繁 → 是内存问题导致 CPU 高，光看 CPU 会查错方向。

---

## 二、原因（按发生频率排序）

### 1. 查询类（最常见）

| 原因 | 为什么烧 CPU |
|---|---|
| **深分页**（`from` 很大，如 `from: 1000000`） | 每个分片都要取出 `from+size` 条再丢弃，白白算一堆 |
| **大聚合**（`terms` 高基数、`cardinality`） | 要在内存里建桶、去重，基数一大就爆炸 |
| **`scripted_metric` 聚合** | 每条文档都执行脚本，CPU 杀手 |
| **`wildcard` 左通配**（`*abc`） | 无法用倒排索引，退化为全扫描 |
| **正则查询** | 同上，且更慢 |
| **`script` 脚本查询** | 每条文档执行一次脚本 |
| **超大 `size`**（如 `size: 100000`） | 一次要构造并排序海量结果 |
| 范围查询跨极长时间 | 命中文档太多 |

**共同点**：都让 ES **必须扫描/计算大量文档**，而不是靠索引直接命中。

### 2. Merge（段合并）

ES 写入不是原地更新，而是**不断生成新段**，后台再合并小段成大段。

**merge 是 CPU + IO 双高**，且**写入越猛、merge 越凶**：

| 触发场景 | 说明 |
|---|---|
| 大批量导入（初始化数据） | 最典型的 merge 风暴 |
| 频繁 `bulk` 小批量写 | 小段多，合并频繁 |
| `refresh_interval` 太小 | 每秒生成新段，段数量暴涨 |
| `force merge` | 手动触发的重度合并，非常吃 CPU |

### 3. 分片问题

| 问题 | 后果 |
|---|---|
| **分片过多**（单索引几百上千分片） | 每个分片都是独立 Lucene 实例，元数据与查询开销叠加 |
| **分片分布不均** | 热点节点 CPU 打满，其他节点闲 |
| 副本数过多 | 每次写入要同步多份 |

### 4. 集群动作

| 动作 | 说明 |
|---|---|
| **Shard Rebalance** | 节点增减时的分片搬迁，CPU + IO 双高 |
| **Recovery** | 节点重启后恢复分片 |
| **Snapshot 备份** | 读全量数据，CPU 与 IO 压力大 |

### 5. GC

堆内存不够 → 频繁 Full GC → CPU 被 GC 线程吃满。
**表现是 CPU 高，根因是内存**。

---

## 三、排查：三条命令定位

### 第 1 步：看哪个节点在烧

```bash
GET _cat/nodes?v&h=name,cpu,load_1m,heap.percent,node.role
```

- `cpu` 高的是热点节点
- 如果**所有节点都高** → 大概率是全局性查询或写入压力
- 如果**只有一个节点高** → 看是不是分片分布不均

### 第 2 步：看是谁在烧（最关键）

```bash
GET _nodes/hot_threads
```

**这是排查 ES CPU 的第一命令**，直接输出热点线程的**完整堆栈**：

```
:: {node-1}{...}
   Hot threads at ..., interval=500ms, busiestThreads=3:
   95.2% (476ms out of 500ms) cpu usage by thread 'elasticsearch[node-1][search][T#3]'
     ...
     org.apache.lucene.search.IndexSearcher.search(...)
     org.elasticsearch.search.aggregations.bucket.terms...
```

**一眼就能看出**：
- `search` 线程 + `aggregations` → 大聚合
- `search` + `IndexSearcher.search` 深堆栈 → 大查询
- `Lucene Merge Thread` → merge
- `G1 Young/Old Generation` → GC 问题

### 第 3 步：看正在跑什么查询

```bash
GET _tasks?detailed=true&actions=*search&human
```

可以看到每个正在执行的查询、**带了什么参数**（这就是在跑的"罪犯"）：

```json
{
  "nodes": {
    "xxx": {
      "tasks": {
        "abc:123": {
          "action": "indices:data/read/search",
          "description": "indices[orders], search_type[QUERY_THEN_FETCH], source[{size:10000, aggs:...}]"
        }
      }
    }
  }
}
```

**`description` 里能看到查询体** —— 是深分页还是大聚合，一目了然。

### 补充命令

```bash
# 线程池压力（queue/rejected 高说明扛不住了）
GET _cat/thread_pool/search?v&h=node_name,active,queue,rejected

# GC 情况
GET _nodes/stats/jvm

# 慢查询日志（需要提前开启）
GET _index/_search?q=...      # 或看 logs/ 下的 *_slowlog.log

# 索引分片数与大小
GET _cat/indices?v&h=index,health,pri,rep,docs.count,store.size
```

### 系统层（ES 之外的确认）

```bash
top -H -p <es_pid>          # 找出烧 CPU 的线程（十六进制 tid）
jstack <es_pid> > t.txt     # 把 tid 转成 16 进制去堆栈里找
```

---

## 四、救火：最快止血的手段（重点）

**核心思路：先让它别烧了，再谈优化。烧着的时候做优化是来不及的。**

按「见效速度」排序：

### 🚑 手段一：直接杀掉问题查询（最一针见血）

**适用**：`_nodes/hot_threads` 显示是 search 线程，`_tasks` 里能看到正在跑的大查询。

```bash
# 1. 找到任务 id
GET _tasks?detailed=true&actions=*search

# 2. 取消它
POST _tasks/<task_id>/_cancel
```

**效果**：**秒级见效**。失控查询一停，CPU 立刻掉下来。

**这是最快、最一针见血的手段**——问题查询往往是那么一两个（比如某个定时任务跑了个大聚合），
杀掉之后集群马上恢复，给你争取到排查时间。

### 🚑 手段二：取消整个搜索任务（批量）

如果要杀的不止一个：

```bash
POST _tasks/_cancel?actions=indices:data/read/search
```

**慎用**：会取消所有搜索，业务查询全部失败。

### 🚑 手段三：停掉写入，让 merge 停下来

**适用**：热点线程是 `Lucene Merge Thread`。

**做法**：
1. 应用侧**暂停批量导入**（这是最有效的，从源头断掉）
2. 临时调大 `refresh_interval`，减少新段产生：

```bash
PUT /<index>/_settings
{ "index.refresh_interval": "60s" }
```

**效果**：**分钟级见效**。写入停了，merge 自然平息。

### 🚑 手段四：限流，保住核心业务

**适用**：不能杀查询（业务在用），但集群快扛不住。

1. **网关/应用层限流**：把请求量降下来
2. **临时降低搜索线程池队列**，让压力快速失败而不是堆积：

```bash
PUT /_cluster/settings
{
  "transient": {
    "thread_pool.search.queue_size": 500
  }
}
```

**思路**：**宁可拒绝一部分请求，也不能让整个集群雪崩**。
队列无上限地堆积，最终会 OOM 或拖垮所有节点。

### 🚑 手段五：临时扩容

**适用**：确认是容量不足，且能拿到机器。

**注意坑**：加节点会触发 **shard rebalance**，
迁移过程本身**也会消耗 CPU 和 IO**，可能短期更卡。所以这一步不是最快的。

### 🚑 手段六：重启节点（最后手段）

**适用**：GC 问题，或节点已无响应。

**注意**：重启后要**恢复分片（recovery）**，
如果重启多个节点，恢复流量会把剩余节点压得更死。

**正确做法**：**一次只重启一个**，等它恢复完再动下一个。

---

## 五、救火速查表

| 现象（hot_threads 里看到的） | 最快手段 |
|---|---|
| `search` + 聚合/深分页 | **`_tasks/_cancel` 杀掉那个查询** |
| `Lucene Merge Thread` | **停写入 + 调大 refresh_interval** |
| GC 线程（`G1 Young/Old`） | 重启节点（一次一个）+ 查内存 |
| 多节点都在跑 search | 应用层限流，优先保核心业务 |
| 全节点均匀高 CPU | 大概率是全局写入压力，先降写入 |

---

## 六、根治：事后要做的优化

### 查询侧

| 问题 | 优化 |
|---|---|
| **深分页** | 改用 **`search_after`**（游标分页），不要用大 `from` |
| 大聚合 | 限制 `size`、避免高基数 `terms`、用 `composite` 分页聚合 |
| 通配符 | 避免左通配 `*xxx`；能改成 `prefix` 就改 |
| 脚本 | 尽量改成内置查询；必须用则限制范围 |
| 返回字段 | 用 `_source` 过滤，别返回全部字段 |
| 一次性拉全量 | 用 `scroll` 或 PIT，且设合理批次 |

**深分页的经典解法**：

```json
// 不要这样
{ "from": 100000, "size": 10 }

// 改成这样（带上上一页最后一条的排序值）
{ "size": 10, "search_after": [1699999999000, "doc_12345"], "sort": [...] }
```

### 写入侧

| 问题 | 优化 |
|---|---|
| 小批量频繁写 | 攒大批量（**每批 5~15MB** 是常见经验值） |
| refresh 太频繁 | `refresh_interval` 设 30s 或更长（写入型索引） |
| merge 压力大 | 调 `index.merge.scheduler.max_thread_count`（HDD 设 1） |
| 副本太多 | 按需降副本数 |

### 架构侧

| 问题 | 优化 |
|---|---|
| 分片过多 | 单分片 **10~50GB** 是比较健康的区间；控制总分片数 |
| 分片不均 | 检查路由、调整分片数 |
| 冷热数据混放 | **冷热分离**：热数据 SSD，冷数据降副本、force merge 后归档 |
| 读写互相影响 | 分离读集群与写集群 |

### 兜底侧

| 措施 | 说明 |
|---|---|
| **慢查询日志** | 提前开启，事后有据可查 |
| **监控告警** | CPU、GC、队列 rejected、慢查询数，阈值告警 |
| **查询超时** | 客户端设 timeout，别让请求无限挂着 |
| **限流** | 网关层按业务分级限流 |
| **定期评审查询** | 新上的查询走 review，避免脚本/大聚合上线 |

---

## 七、记忆口诀

| 问题 | 答案 |
|---|---|
| 第一步看什么 | **`_nodes/hot_threads`** —— 直接看到是谁在烧 |
| 怎么找问题查询 | `_tasks?detailed=true&actions=*search`，看 description |
| **最快的救火** | **`POST _tasks/<id>/_cancel` 杀掉问题查询**（秒级） |
| merge 导致怎么办 | **停写入 + 调大 refresh_interval** |
| GC 导致怎么办 | **一次重启一个节点**，别批量重启 |
| 为什么不能随便扩容 | 扩容触发 rebalance，**迁移本身也烧 CPU** |
| 为什么要限流 | **宁可拒绝一部分，也不能让集群雪崩** |
| 深分页怎么根治 | 改 **`search_after`** |
| 分片多少合适 | 单分片 **10~50GB**，别搞出上千分片 |

**一句话**：

> 先用 `hot_threads` 看清是谁在烧，
> 是查询就 `_cancel`，是 merge 就停写入，
> **先止血，再优化**。
