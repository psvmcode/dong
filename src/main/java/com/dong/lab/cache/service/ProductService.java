package com.dong.lab.cache.service;

import com.dong.lab.cache.dto.ProductSaveRequest;
import com.dong.lab.cache.entity.Product;
import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.result.PageResult;

import java.util.List;

public interface ProductService {

    Product findById(Long id);

    /**
     * 同 findById，区别是先用布隆过滤器判断 id 是否可能存在，
     * 不存在就直接拒绝，连缓存和数据库都不查询。
     */
    Product findByIdGuarded(Long id);

    PageResult<Product> findByPage(PageRequest request);

    List<Product> findAll();

    Long create(ProductSaveRequest request);

    void update(Long id, ProductSaveRequest request);

    void delete(Long id);

    /**
     * 预热：把商品灌进缓存，并把所有 id 写入布隆过滤器。
     * 必须在使用布隆过滤器模式前调用，否则过滤器为空会拒绝一切请求。
     */
    int warmUp();

}
