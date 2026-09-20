# dong 编码规范（参考文档 / 正反例）

规则全部来自 `/Users/dong/items/java/dong` 现有代码。示例可直接照抄结构。

## 1. 空行：方法

方法前后各一个空行，方法体内部不空行。

```java
// 正确
public class ProductServiceImpl implements ProductService {

    /**
     * 分页查询。
     */
    @Override
    public PageResult<Product> findByPage(PageRequest request) {
        List<Product> list = productMapper.selectByPage(request.getOffset(), request.getPageSize());
        return PageResult.of(list, productMapper.countAll(), request);
    }

    /**
     * 查询全部。
     */
    @Override
    public List<Product> findAll() {
        List<Product> all = productMapper.selectAll();
        return all;
    }

}

// 错误：方法体内部塞空行
public List<Product> findAll() {
    List<Product> all = productMapper.selectAll();

    return all;
}
```

## 2. 空行：字段与常量

每个字段、每个常量前面都有空行。

```java
public class RedPacketStockServiceImpl implements RedPacketStockService {

    private static final String COUNT = "lab:redpacket:count:";

    private static final String AMOUNT = "lab:redpacket:amount:";

    private static final Duration TTL = Duration.ofHours(24);

    /**
     * redisService，业务服务层。
     */
    private final RedisService redisService;

}
```

## 3. 注解与类型声明之间不留空行

```java
// 正确
@Data
public class Product {

// 错误
@Data

public class Product {
```

注意：这条只针对「注解 → 类型声明」。方法前、字段前的空行必须保留。

## 4. 成员顺序：Javadoc → 注解 → 声明

```java
/**
 * 支付超时时间，单位分钟。
 */
@Value("${dong.seckill.payment-timeout-minutes:15}")
private int paymentTimeoutMinutes;

/**
 * 回收超时订单。回滚库存后必须清掉售罄标记。
 */
@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
public void releaseUnpaidOrders() {
    ...
}
```

## 5. 注释：写「为什么」，不写「做什么」

```java
// 正确：误判率是业务必须接受的代价，看代码看不出来
private static final double BLOOM_FALSE_POSITIVE = 0.01;

// 正确：说明为什么必须截
// 这个接口没有分页，数据量一旦超出预期就会把内存吃光
if (all.size() > Constants.MAX_BATCH_SIZE) {
    log.warn("findAll exceeds safe limit, truncated: {} > {}", all.size(), Constants.MAX_BATCH_SIZE);
    return all.subList(0, Constants.MAX_BATCH_SIZE);
}

// 错误：复述代码
// 设置 id
this.id = id;
```

类 Javadoc 允许用 `<p>`、`<ul>` 写多段设计说明：

```java
/**
 * 秒杀实现。四道防线依次生效：
 * 限流令牌桶挡住超出承载力的流量，
 * 本地售罄标记让后续请求连 Redis 都不用访问，
 * Lua 脚本保证扣减与去重原子完成，
 * 数据库唯一索引兜底防重复购买。
 */
```

## 6. 实体与 DTO

```java
@Data
public class Product {

    /**
     * 主键
     */
    private Long id;

    /**
     * 商品价格
     */
    private BigDecimal price;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
```

请求 DTO 自己带校验与转换：

```java
public class RedPacketSendRequest {

    /**
     * 红包总金额，单位分。
     */
    @NotNull
    @Min(1)
    private Long totalAmount;

    /**
     * 转换为红包实体。
     */
    public RedPacket toEntity(String packetNo) {
        RedPacket redPacket = new RedPacket();
        redPacket.setPacketNo(packetNo);
        return redPacket;
    }

}
```

响应 DTO 用静态工厂从实体转换：`RedPacketResponse.from(redPacket)`。
不可变值对象直接用 record：`public record GrabReservation(GrabStatus status, long amount, int seq, boolean itemClaimed)`。

## 7. 枚举 + TypeHandler

```java
@Getter
@AllArgsConstructor
public enum RedPacketStatus {

    /**
     * 已创建，红包尚未开始发放。
     */
    CREATED(0),

    /**
     * 已领完，红包金额已被全部领取。
     */
    FINISHED(2);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取红包状态枚举。
     *
     * @param code 状态编码
     * @return 红包状态枚举
     */
    public static RedPacketStatus of(int code) {
        for (RedPacketStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown red packet status " + code);
    }

}
```

TypeHandler 固定写法（`@MappedTypes` + `BaseTypeHandler`，四个方法都带 Javadoc，`wasNull()` 判空）。

## 8. Controller

```java
@RestController
@Validated
@RequestMapping("/api/red-packet")
@RequiredArgsConstructor
@Tag(name = "抢红包")
public class RedPacketController {

    /**
     * redPacketService，业务服务层。
     */
    private final RedPacketService redPacketService;

    /**
     * 抢红包，一次原子弹出即完成。
     */
    @PostMapping("/grab")
    @Operation(summary = "抢红包，从预分配列表中原子弹出一份")
    public Result<GrabResultResponse> grab(@RequestParam
 @NotBlank @Size(max = 128) String packetNo, @RequestParam Long userId) {
        return Result.success(redPacketService.grab(packetNo, userId));
    }

}
```

参数校验边界：

| 场景 | 写法 |
|---|---|
| 必填字符串 | `@NotBlank @Size(max = Constants.MAX_NAME_LENGTH)` |
| 可选字符串 | `@RequestParam(required = false) @Size(max = 128)`，**不能加 `@NotBlank`** |
| 数值上限 | `@Min(1) @Max(Constants.MAX_PAGE_SIZE)` |
| 分页 | `@RequestParam(defaultValue = "1") @Min(1) @Max(Constants.MAX_PAGE_NUM) int pageNum` |
| 枚举字符串 | `@Pattern(regexp = XxxRequest.SORT_PATTERN)`，正则常量放 Request 里 |

## 9. Service 实现

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RedPacketServiceImpl implements RedPacketService {

    /**
     * redPacketMapper，MyBatis Mapper 数据访问层。
     */
    private final RedPacketMapper redPacketMapper;

    /**
     * 发红包，预分配金额落库后预热 Redis 并返回红包编号。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String send(RedPacketSendRequest request) {
        if (request.getTotalCount() > MAX_TOTAL_COUNT) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "total count must not exceed " + MAX_TOTAL_COUNT);
        }
        log.info("red packet sent packetNo={} count={}", packetNo, request.getTotalCount());
        return packetNo;
    }

}
```

## 10. 定时任务

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dong.redpacket", name = "consistency-task-enabled",
        havingValue = "true", matchIfMissing = true)
public class RedPacketConsistencyTask {

    /**
     * 单次扫描的红包数量，防止红包很多时一次捞全量。
     */
    @Value("${dong.redpacket.consistency-scan-limit:50}")
    private int scanLimit;

    /**
     * 对账未结束的红包，无差异时保持安静。
     */
    @Scheduled(fixedDelayString = "${dong.redpacket.consistency-interval-ms:300000}", initialDelay = 60_000)
    public void reconcile() {
        try {
            redPacketMapper.selectUnfinished(scanLimit).forEach(this::check);
        } catch (Exception ex) {
            log.error("red packet reconciliation failed", ex);
        }
    }

}
```

## 11. 无状态组件（support）

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class GrabRateLimiter {

    private static final String PREFIX = "lab:redpacket:rl:user:";

    /**
     * 限流管理器。
     */
    private final RateLimitManager rateLimitManager;

    /**
     * 是否开启单用户限流。
     */
    @Value("${dong.redpacket.grab-limit-enabled:true}")
    private boolean enabled;

}
```

## 12. Mapper XML

```xml
<mapper namespace="com.dong.cache.mapper.ProductMapper">

    <resultMap id="productMap" type="com.dong.cache.entity.Product">
        <id column="id" property="id"/>
        <result column="status" property="status" javaType="com.dong.cache.enums.ProductStatus"/>
    </resultMap>

    <sql id="columns">
        id, name, category, status, create_time, update_time
    </sql>

    <!-- 分页查询，手写 limit -->
    <select id="selectByPage" resultMap="productMap">
        select
        <include refid="columns"/>
        from product
        order by id asc
        limit #{offset}, #{size}
    </select>

    <!-- 扣减剩余，充足条件写在 where 里防超发 -->
    <update id="decreaseRemain">
        update red_packet
        set remain_count = remain_count - #{count}
        where packet_no = #{packetNo}
          and remain_count >= #{count}
    </update>

</mapper>
```

## 13. DDL

```sql
create table if not exists red_packet_item
(
    id          bigint unsigned not null auto_increment                  comment '主键',
    packet_no   varchar(32)     not null                                 comment '所属红包编号',
    amount      bigint          not null default 0                       comment '预分配金额，单位分',
    status      tinyint         not null default 0                       comment '状态：0 未领取 1 已领取',
    create_time datetime        not null default current_timestamp       comment '创建时间',
    update_time datetime        not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (id),
    unique key uk_packet_seq (packet_no, seq)                                comment '同一红包内序号唯一',
    key idx_packet_status (packet_no, status)                                comment '按红包扫未领取份额'
) engine = innodb
  default charset = utf8mb4
  comment = '红包预分配份额。金额在发红包时算好并落库';
```

改完 `db/schema.sql` 必须跑 `./deploy/gen-initdb.sh`；`deploy/initdb/` 与 `deploy/initdb-replica/` 是生成物，禁止手工编辑。

## 14. 配置

```yaml
dong:
  redpacket:
    # 单用户每分钟最多抢多少次，挡的是脚本刷，与全局按 IP 限流互补
    grab-limit-enabled: true
    grab-limit-per-minute: 60
    stock-ttl: 24h
```

注释只写「为什么是这个值」和默认值语义，不写配置项名字的复述。

## 15. 语言特性

```java
// switch 表达式
return switch (status) {
    case SOLD_OUT -> "red packet is finished";
    case DUPLICATED -> "already grabbed";
    default -> "grab failed";
};

// 有序返回用 LinkedHashMap，计数用 LongAdder，随机用 ThreadLocalRandom
Map<String, Object> runtime = new LinkedHashMap<>(metrics.snapshot());
int seqFrom = ThreadLocalRandom.current().nextInt(MAX_TOTAL_COUNT);

// 布尔包装判空
if (Boolean.TRUE.equals(flags.getIfPresent(packetNo))) { ... }

// 金额比较用 compareTo，不用 equals（小数位不同会误判）
```

## 16. 交付前自检

1. 文件末尾空行；注解与类型声明之间无空行
2. 方法/字段前后空行正确，方法体内部无多余空行
3. 新 Mapper 包已登记到 `config/PrimaryMybatisConfig`
4. 新表已进 `db/schema.sql`，并重新生成 `deploy/initdb*`
5. 新组件带 `@ConditionalOnProperty`，关开关返回 1004 而不是 1005
6. `mvn -q clean compile` 通过
