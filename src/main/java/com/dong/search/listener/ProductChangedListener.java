package com.dong.search.listener;

import com.dong.cache.event.ProductChangedEvent;
import com.dong.search.service.SearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 商品变更监听器，负责把数据库的变更实时推给 Elasticsearch。
 *
 * <p>挂在事务提交之后执行：事务里发出去的事件如果立刻消费，读到的可能是还没提交的数据，
 * 甚至事务最终回滚了而索引已经写脏。提交后再同步，写进索引的一定是真正落库的那一份。
 *
 * <p>这里只带 id 回查数据库，见 {@link ProductChangedEvent}。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ProductChangedListener {

    /**
     * searchSyncService，索引同步服务。
     */
    private final SearchSyncService searchSyncService;

    /**
     * 单条同步。异常必须在这里吃掉：提交后回调里抛出的异常会往上冒到调用事务的方法，
     * 结果就是数据库明明已经提交成功，商品接口却返回失败。
     * 索引没同步上只该留一条日志，剩下的交给定时对账兜底。
     *
     * @param event 商品变更事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onProductChanged(ProductChangedEvent event) {
        try {
            searchSyncService.syncOne(event.getProductId());
        } catch (Exception ex) {
            log.error("failed to sync product {} into elasticsearch, will be repaired by reconciliation", event.getProductId(), ex);
        }
    }

}
