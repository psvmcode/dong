package com.dong.lab.search.controller;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.result.Result;
import com.dong.lab.search.dto.ProductSearchRequest;
import com.dong.lab.search.dto.ProductSearchResponse;
import com.dong.lab.search.service.SearchService;
import com.dong.lab.search.service.SearchSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "search")
public class SearchController {

    private final ObjectProvider<SearchService> searchServiceProvider;

    private final ObjectProvider<SearchSyncService> searchSyncServiceProvider;

    @GetMapping
    @Operation(summary = "full text search with filters and highlighting")
    public Result<ProductSearchResponse> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) Double minPrice,
                                                @RequestParam(required = false) Double maxPrice,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setKeyword(keyword);
        request.setCategory(category);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        return Result.success(requireSearchService().search(request));
    }

    @PostMapping("/sync")
    @Operation(summary = "reindex every product from mysql into elasticsearch")
    public Result<Integer> sync() {
        return Result.success(requireSyncService().syncAll());
    }

    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(requireSearchService().count());
    }

    private SearchService requireSearchService() {
        SearchService service = searchServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED,
                    "set lab.elasticsearch.enabled=true first");
        }
        return service;
    }

    private SearchSyncService requireSyncService() {
        SearchSyncService service = searchSyncServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED,
                    "set lab.elasticsearch.enabled=true first");
        }
        return service;
    }

}
