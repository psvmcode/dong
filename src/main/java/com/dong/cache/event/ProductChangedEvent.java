package com.dong.cache.event;

/**
 * 商品变更事件。
 *
 * <p>只携带商品 id，不携带变更后的实体。原因有两个：
 * 一是监听方在事务提交之后才消费，此时回查数据库拿到的才是已提交的最终值，
 * 带实体等于把事务内的快照传出去，并发更新时会用旧值覆盖索引；
 * 二是相同 id 的重复事件天然幂等，消费顺序颠倒也不会把索引写成中间状态。
 *
 * <p>也不区分新增、更新、删除：监听方回查数据库时查不到就按删除处理，
 * 调用方少维护一个可能填错的操作类型。
 */
public class ProductChangedEvent {

    /**
     * 发生变更的商品 id。
     */
    private final Long productId;

    public ProductChangedEvent(Long productId) {
        this.productId = productId;
    }

    /**
     * 获取商品 id。
     *
     * @return 商品 id
     */
    public Long getProductId() {
        return productId;
    }

}
