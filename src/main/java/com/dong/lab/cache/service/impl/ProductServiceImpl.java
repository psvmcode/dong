package com.dong.lab.cache.service.impl;

import com.dong.lab.cache.dto.ProductSaveRequest;
import com.dong.lab.cache.entity.Product;
import com.dong.lab.cache.mapper.ProductMapper;
import com.dong.lab.cache.service.ProductService;
import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.result.PageResult;
import com.dong.lab.framework.bloom.BloomFilterService;
import com.dong.lab.framework.cache.CacheStats;
import com.dong.lab.framework.cache.MultiLevelCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
/**
 * ProductServiceImpl，Product 业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ProductServiceImpl implements ProductService {

    private static final String CACHE_KEY_PREFIX = "product:";

    private static final String BLOOM_NAME = "lab:bloom:product";

    private static final long BLOOM_EXPECTED = 1_000_000L;

    // 误判率 1%，即每 100 个不存在的 id 约有 1 个会被放过，需要业务层能容忍
    private static final double BLOOM_FALSE_POSITIVE = 0.01;

    private static final Duration PRODUCT_TTL = Duration.ofMinutes(10);

    /**
     * productMapper，MyBatis Mapper 数据访问层。
     */
    private final ProductMapper productMapper;

    /**
     * multiLevelCache，缓存组件。
     */
    private final MultiLevelCache multiLevelCache;

    /**
     * bloomFilterService，业务服务层。
     */
    private final BloomFilterService bloomFilterService;

    /**
     * cacheStats。
     */
    private final CacheStats cacheStats;

    /**
     * 走完整多级缓存链路。防穿透只靠缓存空值标记，
     * 因此针对不存在 id 的重复请求仍会有一批落到回源逻辑上。
     */
    @Override
    public Product findById(Long id) {
        Product product = multiLevelCache.get(cacheKey(id), Product.class, PRODUCT_TTL,
                () -> productMapper.selectById(id));
        if (product == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "product " + id + " not found");
        }
        return product;
    }

    /**
     * 布隆过滤器前置拦截。挡在缓存之前，不存在的 id 连缓存都不会查，
     * 这是防穿透更彻底的做法，代价是需要预热且有误判率。
     */
    @Override
    public Product findByIdGuarded(Long id) {
        RBloomFilter<String> filter = bloomFilterService.getOrCreate(BLOOM_NAME, BLOOM_EXPECTED, BLOOM_FALSE_POSITIVE);
        if (!filter.contains(String.valueOf(id))) {
            cacheStats.recordPenetrationBlocked();
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "product " + id + " rejected by bloom filter");
        }
        return findById(id);
    }

    @Override
    /**
     * 分页查询。
     */
    public PageResult<Product> findByPage(PageRequest request) {
        List<Product> list = productMapper.selectByPage(request.getOffset(), request.getPageSize());
        return PageResult.of(list, productMapper.countAll(), request);
    }

    @Override
    /**
     * 查询全部。
     */
    public List<Product> findAll() {
        return productMapper.selectAll();
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
        RBloomFilter<String> filter = bloomFilterService.getOrCreate(BLOOM_NAME, BLOOM_EXPECTED, BLOOM_FALSE_POSITIVE);
        filter.add(String.valueOf(product.getId()));
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
        productMapper.update(existing);
        multiLevelCache.invalidateEventually(cacheKey(id));
        log.info("product updated id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 删除关注关系。
     */
    public void delete(Long id) {
        productMapper.deleteById(id);
        multiLevelCache.invalidateEventually(cacheKey(id));
        log.info("product deleted id={}", id);
    }

    /**
     * 预热两步：先把商品写入缓存，再把所有 id 加入布隆过滤器。
     * 第二步不能省，否则过滤器为空，guarded 模式会拒绝所有请求。
     */
    @Override
    public int warmUp() {
        List<Product> products = productMapper.selectAll();
        products.forEach(product -> multiLevelCache.get(cacheKey(product.getId()), Product.class, PRODUCT_TTL,
                () -> product));
        RBloomFilter<String> filter = bloomFilterService.getOrCreate(BLOOM_NAME, BLOOM_EXPECTED, BLOOM_FALSE_POSITIVE);
        productMapper.selectAllIds().forEach(id -> filter.add(String.valueOf(id)));
        log.info("cache warmed up with {} products", products.size());
        return products.size();
    }

    /**
     * cacheKey。
     */
    private String cacheKey(Long id) {
        return CACHE_KEY_PREFIX + id;
    }

}
