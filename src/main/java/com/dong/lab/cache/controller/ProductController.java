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

/**
 * 商品接口。它是缓存实验的载体，
 * findById 与 findByIdGuarded 构成一对可直接对比的读路径。
 */
@RestController
@RequestMapping("/api/cache/products")
@RequiredArgsConstructor
@Tag(name = "缓存-商品")
public class ProductController {

    private final ProductService productService;

    /**
     * 普通读路径，走完 L1、L2、回源三级。
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询商品，依次经过 L1、L2 和数据库")
    public Result<ProductResponse> findById(@PathVariable Long id) {
        return Result.success(ProductResponse.from(productService.findById(id)));
    }

    /**
     * 布隆过滤版读路径。与上一个接口形成对照，
     * 用同一个不存在的 id 分别请求，可以直观看到耗时差异。
     */
    @GetMapping("/{id}/guarded")
    @Operation(summary = "查询商品，id 不可能存在时由布隆过滤器提前拒绝")
    public Result<ProductResponse> findByIdGuarded(@PathVariable Long id) {
        return Result.success(ProductResponse.from(productService.findByIdGuarded(id)));
    }

    /**
     * 分页查询刻意不经过缓存。列表查询的组合太多，缓存命中率低，
     * 强行缓存只会带来更高的失效成本。
     */
    @GetMapping
    @Operation(summary = "分页查询商品，有意不经过缓存")
    public Result<PageResult<ProductResponse>> findByPage(@RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<com.dong.lab.cache.entity.Product> page = productService.findByPage(PageRequest.of(pageNum, pageSize));
        return Result.success(page.map(ProductResponse::from));
    }

    /**
     * 全量列表，供预热等场景使用，同样不走缓存。
     */
    @GetMapping("/all")
    @Operation(summary = "查询全部商品，不经过缓存")
    public Result<List<ProductResponse>> findAll() {
        return Result.success(productService.findAll().stream().map(ProductResponse::from).toList());
    }

    /**
     * 新增。会同步写入布隆过滤器，否则新商品会被判为不存在。
     */
    @PostMapping
    @Operation(summary = "新增商品，并同步写入布隆过滤器")
    public Result<Long> create(@Valid @RequestBody ProductSaveRequest request) {
        return Result.success(productService.create(request));
    }

    /**
     * 更新。先改库再失效缓存，并用延迟双删兜底。
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新商品，先更新数据库再失效缓存")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductSaveRequest request) {
        productService.update(id, request);
        return Result.success();
    }

    /**
     * 删除。同样用延迟双删保证各节点缓存一致。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品，并失效对应缓存")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

}
