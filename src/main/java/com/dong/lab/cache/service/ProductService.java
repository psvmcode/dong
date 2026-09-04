package com.dong.lab.cache.service;

import com.dong.lab.cache.dto.ProductSaveRequest;
import com.dong.lab.cache.entity.Product;
import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.result.PageResult;

import java.util.List;

/**
 * 商品服务接口。
 */
public interface ProductService {

    /**
     * 根据 id 查询。
     */
    Product findById(Long id);

    /**
     * 同 findById，区别是先用布隆过滤器判断 id 是否可能存在，
     * 不存在就直接拒绝，连缓存和数据库都不查询。
     */
    Product findByIdGuarded(Long id);

    /**
     * 分页查询。
     */
    PageResult<Product> findByPage(PageRequest request);

    /**
     * 查询全部。
     */
    List<Product> findAll();

    /**
     * 创建记录。
     */
    Long create(ProductSaveRequest request);

    /**
     * 更新记录，返回影响行数。
     */
    void update(Long id, ProductSaveRequest request);

    /**
     * 删除关注关系。
     */
    void delete(Long id);

    /**
     * 预热：把商品灌进缓存，并把所有 id 写入布隆过滤器。
     * 必须在使用布隆过滤器模式前调用，否则过滤器为空会拒绝一切请求。
     */
    int warmUp();

}
