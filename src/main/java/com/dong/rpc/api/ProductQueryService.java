package com.dong.rpc.api;

import com.dong.rpc.dto.ProductQueryRequest;
import com.dong.rpc.dto.ProductQueryResponse;

/**
 * 商品查询服务。实现落在 search 模块，调用方只依赖这个接口。
 *
 * <p>这是「跨模块通信」的真实示例：rpc 模块不引用 search 的任何类，
 * search 模块自己实现这个接口并打上 @RpcService。
 * 于是编译期依赖是单向的（业务 → 基础设施），运行期却能调到业务模块的能力。
 */
public interface ProductQueryService {

    /**
     * 按关键字查询商品。
     *
     * @param request 查询条件
     * @return 查询结果
     */
    ProductQueryResponse query(ProductQueryRequest request);

}
