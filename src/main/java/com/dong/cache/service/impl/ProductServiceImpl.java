package com.dong.cache.service.impl;

import com.dong.cache.dto.ProductReadResult;
import com.dong.cache.dto.ProductSaveRequest;
import com.dong.cache.entity.Product;
import com.dong.cache.event.ProductChangedEvent;
import com.dong.cache.mapper.ProductMapper;
import com.dong.cache.service.ProductService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import com.dong.framework.bloom.BloomFilterService;
import com.dong.framework.cache.CacheResolution;
import com.dong.framework.cache.CacheStats;
import com.dong.framework.cache.MultiLevelCache;
import com.dong.framework.lock.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * 商品服务实现。
 *
 * <p>增删改都在事务提交后各发布一次商品变更事件，搜索模块据此把数据同步进 Elasticsearch。
 * 这里只发事件、不直接调同步服务：一是缓存模块不该反向依赖搜索模块，
 * 二是 ES 抖动时同步必然失败，不能让它牵连商品写入这条主流程。
 *
 * <p>读路径上有一个明确的取舍：不再保证「每次都返回数据」。
 * 缓存和数据库都拿不到时抛 1005 让调用方明确失败，
 * 只有在确实缓存过旧值时才返回 {@code stale} 标记的数据。
 * 硬凑一个结果出来看着友好，实际是用错误数据掩盖故障，
 * 而且会把故障期间的空值写进缓存，让故障结束后还得等标记过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String CACHE_KEY_PREFIX = "product:";

    private static final String BLOOM_NAME = "lab:bloom:product";

    private static final String WARM_UP_LOCK = "lab:cache:warm-up";

    private static final Duration WARM_UP_LEASE = Duration.ofMinutes(5);

    private static final long BLOOM_EXPECTED = 1_000_000L;

    // 误判率 1%，即每 100 个不存在的 id 约有 1 个会被放过，需要业务层能容忍
    private static final double BLOOM_FALSE_POSITIVE = 0.01;

    private static final Duration PRODUCT_TTL = Duration.ofMinutes(10);

    /**
     * 商品数据访问接口。
     */
    private final ProductMapper productMapper;

    /**
     * 多级缓存组件。
     */
    private final MultiLevelCache multiLevelCache;

    /**
     * 布隆过滤器服务。
     */
    private final BloomFilterService bloomFilterService;

    /**
     * 缓存命中统计组件。
     */
    private final CacheStats cacheStats;

    /**
     * 分布式锁服务，用于预热防重入。
     */
    private final DistributedLockService distributedLockService;

    /**
     * 商品变更事件发布器，由搜索模块在事务提交后消费。
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 布隆过滤器实例，惰性初始化后复用。
     */
    private volatile RBloomFilter<String> productBloom;

    /**
     * 走完整多级缓存链路。防穿透只靠缓存空值标记，
     * 因此针对不存在 id 的重复请求仍会有一批落到回源逻辑上。
     */
    @Override
    public ProductReadResult findById(Long id) {
        CacheResolution<Product> resolution = multiLevelCache.resolve(cacheKey(id), Product.class, PRODUCT_TTL,
                () -> productMapper.selectById(id));
        return switch (resolution) {
            case CacheResolution.Fresh<Product> fresh -> ProductReadResult.fresh(fresh.value());
            case CacheResolution.Stale<Product> stale -> ProductReadResult.stale(stale.value());
            case CacheResolution.Absent<Product> absent ->
                    throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "product " + id + " not found");
            case CacheResolution.Unavailable<Product> unavailable ->
                    throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "product " + id + " is temporarily unavailable, please retry later");
        };
    }

    /**
     * 布隆过滤器前置拦截。挡在缓存之前，不存在的 id 连缓存都不会查，
     * 这是防穿透更彻底的做法，代价是需要预热且有误判率。
     */
    @Override
    public ProductReadResult findByIdGuarded(Long id) {
        if (bloomRejects(id)) {
            cacheStats.recordPenetrationBlocked();
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "product " + id + " rejected by bloom filter");
        }
        return findById(id);
    }

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
        // 封顶交给 SQL 而不是捞回来再截：ResultSet 和 List 会先吃满内存，
        // 等截断发生时该吃的内存已经吃下去了
        return productMapper.selectTop(Constants.MAX_BATCH_SIZE);
    }

    /**
     * 新增商品。注意必须同步写布隆过滤器，
     * 否则新数据在过滤器里不存在，之后会被误判为穿透请求直接拒绝。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductSaveRequest request) {
        Product product = request.toEntity();
        productMapper.insert(product);
        addToBloom(product.getId());
        eventPublisher.publishEvent(new ProductChangedEvent(product.getId()));
        log.info("product created id={}", product.getId());
        return product.getId();
    }

    /**
     * 更新商品。先改库再失效缓存，并用延迟双删兜底，
     * 顺序不能颠倒，否则并发读可能把旧值重新写回缓存。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductSaveRequest request) {
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "product " + id + " not found");
        }
        existing.setName(request.getName());
        existing.setCategory(request.getCategory());
        existing.setPrice(request.getPrice());
        existing.setStock(request.getStock());
        existing.setLongitude(request.getLongitude());
        existing.setLatitude(request.getLatitude());
        existing.setDescription(request.getDescription());
        productMapper.update(existing);
        multiLevelCache.invalidateEventually(cacheKey(id));
        eventPublisher.publishEvent(new ProductChangedEvent(id));
        log.info("product updated id={}", id);
    }

    /**
     * 删除关注关系。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        productMapper.deleteById(id);
        multiLevelCache.invalidateEventually(cacheKey(id));
        eventPublisher.publishEvent(new ProductChangedEvent(id));
        log.info("product deleted id={}", id);
    }

    /**
     * 预热两步：先把商品写入缓存，再把所有 id 加入布隆过滤器。
     * 第二步不能省，否则过滤器为空，guarded 模式会拒绝所有请求。
     *
     * <p>预热要扫全表并逐条写缓存，并发触发只会互相拖慢，
     * 因此整体加分布式锁串行掉，拿不到锁说明有一轮还在跑，直接报冲突。
     */
    @Override
    public int warmUp() {
        return distributedLockService.execute(WARM_UP_LOCK, WARM_UP_LEASE, Duration.ofMillis(100), this::doWarmUp);
    }

    /**
     * 执行预热。写缓存失败不影响返回值，缓存本来就是可以重建的副本。
     *
     * @return 预热商品数量
     */
    private int doWarmUp() {
        List<Product> products = productMapper.selectAll();
        products.forEach(product -> multiLevelCache.get(cacheKey(product.getId()), Product.class, PRODUCT_TTL,
                () -> product));
        try {
            RBloomFilter<String> filter = bloomFilter();
            productMapper.selectAllIds().forEach(id -> filter.add(String.valueOf(id)));
        } catch (Exception ex) {
            // 预热没写进过滤器，后果是 guarded 模式会误拒真实 id，
            // 这比让整个预热失败要好，而且可以重跑一次修复
            log.warn("bloom filter warm up failed, guarded reads may reject existing ids: {}", ex.getMessage());
        }
        log.info("cache warmed up with {} products", products.size());
        return products.size();
    }

    /**
     * 用布隆过滤器判断是否应直接拒绝。
     * 过滤器依赖 Redis，它不可用时一律放行：
     * 拒绝会让「Redis 抖一下」变成「所有商品都查不到」，典型的把中间件故障放大成业务不可用。
     *
     * @param id 商品 id
     * @return 是否应拒绝
     */
    private boolean bloomRejects(Long id) {
        try {
            return !bloomFilter().contains(String.valueOf(id));
        } catch (Exception ex) {
            log.warn("bloom filter unavailable, skipping guard for id={}: {}", id, ex.getMessage());
            return false;
        }
    }

    /**
     * 取布隆过滤器，实例只初始化一次。
     * 必须缓存的原因：Redisson 的 tryInit 不是本地幂等判断，每次都会向 Redis 发命令。
     * 不缓存的话，这个本该「比查缓存更便宜」的前置拦截，
     * 每次反而要两次 Redis 往返，比它要保护的查询还贵。
     *
     * @return 布隆过滤器
     */
    private RBloomFilter<String> bloomFilter() {
        RBloomFilter<String> filter = productBloom;
        if (filter == null) {
            filter = bloomFilterService.getOrCreate(BLOOM_NAME, BLOOM_EXPECTED, BLOOM_FALSE_POSITIVE);
            productBloom = filter;
        }
        return filter;
    }

    /**
     * 把 id 加入布隆过滤器。失败只告警不回滚事务：
     * 商品已经写进数据库，为了缓存索引牺牲主流程不值得，重跑预热即可补上。
     *
     * @param id 商品 id
     */
    private void addToBloom(Long id) {
        try {
            bloomFilter().add(String.valueOf(id));
        } catch (Exception ex) {
            log.warn("bloom filter add failed for id={}: {}", id, ex.getMessage());
        }
    }

    /**
     * 构建商品缓存键。
     *
     * @param id 商品 id
     * @return 缓存键
     */
    private String cacheKey(Long id) {
        return CACHE_KEY_PREFIX + id;
    }

}
