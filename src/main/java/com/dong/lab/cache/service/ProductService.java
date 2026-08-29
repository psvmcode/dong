package com.dong.lab.cache.service;

import com.dong.lab.cache.dto.ProductSaveRequest;
import com.dong.lab.cache.entity.Product;
import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.result.PageResult;

import java.util.List;

public interface ProductService {

    Product findById(Long id);

    Product findByIdGuarded(Long id);

    PageResult<Product> findByPage(PageRequest request);

    List<Product> findAll();

    Long create(ProductSaveRequest request);

    void update(Long id, ProductSaveRequest request);

    void delete(Long id);

    int warmUp();

}
