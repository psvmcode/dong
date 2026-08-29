package com.dong.lab.cache.controller;

import com.dong.lab.cache.dto.ProductResponse;
import com.dong.lab.cache.dto.ProductSaveRequest;
import com.dong.lab.cache.service.ProductService;
import com.dong.lab.common.result.PageRequest;
import com.dong.lab.common.result.PageResult;
import com.dong.lab.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cache/products")
@RequiredArgsConstructor
@Tag(name = "cache-product")
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{id}")
    @Operation(summary = "read through level 1, level 2 and the database")
    public Result<ProductResponse> findById(@PathVariable Long id) {
        return Result.success(ProductResponse.from(productService.findById(id)));
    }

    @GetMapping("/{id}/guarded")
    @Operation(summary = "same read, rejected early by the bloom filter when the id cannot exist")
    public Result<ProductResponse> findByIdGuarded(@PathVariable Long id) {
        return Result.success(ProductResponse.from(productService.findByIdGuarded(id)));
    }

    @GetMapping
    @Operation(summary = "paged list, bypasses the cache on purpose")
    public Result<PageResult<ProductResponse>> findByPage(@RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<com.dong.lab.cache.entity.Product> page = productService.findByPage(PageRequest.of(pageNum, pageSize));
        return Result.success(page.map(ProductResponse::from));
    }

    @GetMapping("/all")
    public Result<List<ProductResponse>> findAll() {
        return Result.success(productService.findAll().stream().map(ProductResponse::from).toList());
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProductSaveRequest request) {
        return Result.success(productService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest request) {
        productService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

}
