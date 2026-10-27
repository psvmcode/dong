package com.dong.search.rpc;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.rpc.annotation.RpcService;
import com.dong.rpc.api.ProductQueryService;
import com.dong.rpc.dto.ProductItem;
import com.dong.rpc.dto.ProductQueryRequest;
import com.dong.rpc.dto.ProductQueryResponse;
import com.dong.search.dto.ProductSearchRequest;
import com.dong.search.dto.ProductSearchResponse;
import com.dong.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 search 模块的检索能力暴露成 RPC 服务。
 *
 * <p>这是「模块之间通信」的真实一例：实现类在 search 模块，接口在 rpc 模块，
 * 调用方编译时只需要接口，运行时由注册中心决定连到哪台机器。
 * 依赖方向是单向的（search → rpc），rpc 不反过来引用 search 的任何类。
 *
 * <p>按 ES 开关启停：ES 没开时连 Bean 都不注册，
 * 于是注册中心里也不会出现这个服务，调用方得到的是明确的「没有可用提供者」，
 * 而不是「连上了但一调用就报错」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@RpcService(interfaceClass = ProductQueryService.class)
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
public class ProductSearchRpcService implements ProductQueryService {

    /**
     * searchServiceProvider，检索服务提供者。
     */
    private final ObjectProvider<SearchService> searchServiceProvider;

    @Override
    public ProductQueryResponse query(ProductQueryRequest request) {
        SearchService searchService = searchServiceProvider.getIfAvailable();
        if (searchService == null) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "set dong.elasticsearch.enabled=true first");
        }
        ProductSearchRequest searchRequest = new ProductSearchRequest();
        searchRequest.setKeyword(request.getKeyword());
        searchRequest.setPageNum(request.getPageNum());
        searchRequest.setPageSize(request.getPageSize());
        ProductSearchResponse searchResponse = searchService.search(searchRequest);
        ProductQueryResponse response = new ProductQueryResponse();
        response.setTotal(searchResponse.getTotal());
        response.setItems(toItems(searchResponse.getList()));
        log.info("rpc query keyword={} total={}", request.getKeyword(), response.getTotal());
        return response;
    }

    /**
     * 转换商品条目。
     *
     * @param hits 检索命中
     * @return 商品条目
     */
    private List<ProductItem> toItems(List<ProductSearchResponse.Hit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<ProductItem> items = new ArrayList<>(hits.size());
        for (ProductSearchResponse.Hit hit : hits) {
            ProductItem item = new ProductItem();
            item.setId(hit.getId());
            item.setName(hit.getName());
            item.setCategory(hit.getCategory());
            // 库里是 BigDecimal，跨服务传输用 double：演示查询对精度没有要求，
            // 真要做资金相关的传输必须换成分为单位的长整数，浮点会丢精度
            item.setPrice(hit.getPrice() == null ? 0D : hit.getPrice().doubleValue());
            items.add(item);
        }
        return items;
    }

}
