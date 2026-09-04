package com.dong.lab.search.service.impl;

import com.dong.lab.cache.entity.Product;
import com.dong.lab.cache.mapper.ProductMapper;
import com.dong.lab.search.entity.ProductDocument;
import com.dong.lab.search.service.SearchService;
import com.dong.lab.search.service.SearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * 索引同步实现，把 MySQL 全量商品重建到 Elasticsearch。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "lab.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor

public class SearchSyncServiceImpl implements SearchSyncService {

    /**
     * productMapper，MyBatis Mapper 数据访问层。
     */
    private final ProductMapper productMapper;

    /**
     * searchService，业务服务层。
     */
    private final SearchService searchService;

    @Override
    /**
     * syncAll。
     */
    public int syncAll() {
        List<Product> products = productMapper.selectAll();
        List<ProductDocument> documents = products.stream().map(this::toDocument).toList();
        searchService.bulkIndex(documents);
        log.info("synced {} products into elasticsearch", documents.size());
        return documents.size();
    }

    /**
     * toDocument。
     */
    private ProductDocument toDocument(Product product) {
        ProductDocument document = new ProductDocument();
        document.setId(String.valueOf(product.getId()));
        document.setName(product.getName());
        document.setCategory(product.getCategory());
        document.setDescription(product.getName() + " " + product.getCategory());
        document.setPrice(product.getPrice());
        document.setStock(product.getStock());
        document.setStatus(product.getStatus() == null ? null : product.getStatus().name());
        document.setCreateTime(product.getCreateTime());
        return document;
    }

}
