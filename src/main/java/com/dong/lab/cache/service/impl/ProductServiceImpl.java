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

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final String CACHE_KEY_PREFIX = "product:";

    private static final String BLOOM_NAME = "lab:bloom:product";

    private static final long BLOOM_EXPECTED = 1_000_000L;

    private static final double BLOOM_FALSE_POSITIVE = 0.01;

    private static final Duration PRODUCT_TTL = Duration.ofMinutes(10);

    private final ProductMapper productMapper;

    private final MultiLevelCache multiLevelCache;

    private final BloomFilterService bloomFilterService;

    private final CacheStats cacheStats;

    @Override
    public Product findById(Long id) {
        Product product = multiLevelCache.get(cacheKey(id), Product.class, PRODUCT_TTL,
                () -> productMapper.selectById(id));
        if (product == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "product " + id + " not found");
        }
        return product;
    }

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
    public PageResult<Product> findByPage(PageRequest request) {
        List<Product> list = productMapper.selectByPage(request.getOffset(), request.getPageSize());
        return PageResult.of(list, productMapper.countAll(), request);
    }

    @Override
    public List<Product> findAll() {
        return productMapper.selectAll();
    }

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
    public void delete(Long id) {
        productMapper.deleteById(id);
        multiLevelCache.invalidateEventually(cacheKey(id));
        log.info("product deleted id={}", id);
    }

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

    private String cacheKey(Long id) {
        return CACHE_KEY_PREFIX + id;
    }

}
