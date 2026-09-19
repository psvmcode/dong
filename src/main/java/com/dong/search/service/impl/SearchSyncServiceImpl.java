package com.dong.search.service.impl;

import com.dong.cache.entity.Product;
import com.dong.cache.mapper.ProductMapper;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.search.dto.ConsistencyReport;
import com.dong.search.entity.ProductDocument;
import com.dong.search.service.SearchService;
import com.dong.search.service.SearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 索引同步实现。
 *
 * <p>一致性靠三条路径叠加，单独任何一条都不够：
 * 实时同步保证正常情况下不漂移；手动接口用于已知出问题时的止血；
 * 定期对账负责收敛实时同步漏掉的——ES 当时不可用、进程刚提交完就挂掉、有人绕过应用直接改库删索引。
 *
 * <p>对账必须两边都取全量。只要有一侧被截断，「多出来的那部分」就会被当成孤儿文档删掉，
 * 所以超过容量上限时宁可拒绝执行，也不做近似对账。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "dong.elasticsearch", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SearchSyncServiceImpl implements SearchSyncService {

    /**
     * 一轮对账能处理的条数上限。对账是一次全量比对：库里全量拉出来，索引里全量拉出来，
     * 两边都在内存里按 id 配对，所以这个上限本质上是内存上限，不是能力上限。
     * 超过说明该改成分页对账或离线任务，而不是把数据截断了事。
     */
    private static final int MAX_RECONCILE_SIZE = Constants.MAX_BATCH_SIZE;

    /**
     * productMapper，商品数据访问层。
     */
    private final ProductMapper productMapper;

    /**
     * searchService，索引读写服务。
     */
    private final SearchService searchService;

    /**
     * 全量同步。索引没有增量概念，同一条重复写入等于覆盖，重建本身是幂等的。
     * 结束时刷新一次，让这次重建的结果立刻能被查询验证。
     */
    @Override
    public int syncAll() {
        List<Product> products = productMapper.selectAll();
        List<ProductDocument> documents = products.stream().map(this::toDocument).toList();
        searchService.bulkIndex(documents);
        int removed = removeOrphans(products);
        searchService.refresh();
        log.info("synced {} products into elasticsearch, removed {} orphan document(s)", documents.size(), removed);
        return documents.size();
    }

    /**
     * 单条同步。
     */
    @Override
    public void syncOne(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            searchService.deleteById(String.valueOf(productId));
            log.info("product {} is gone from database, removed from index", productId);
            return;
        }
        searchService.index(toDocument(product));
    }

    /**
     * checkConsistency。
     */
    @Override
    public ConsistencyReport checkConsistency() {
        return reconcile(false);
    }

    /**
     * repairConsistency。
     */
    @Override
    public ConsistencyReport repairConsistency() {
        return reconcile(true);
    }

    /**
     * 对账主流程：两边取全量，按 id 分出三类差异，再按需修复。
     *
     * @param repair 是否执行修复
     * @return 对账报告
     */
    private ConsistencyReport reconcile(boolean repair) {
        List<Product> products = productMapper.selectAll();
        long esCount = searchService.count();
        if (products.size() > MAX_RECONCILE_SIZE || esCount > MAX_RECONCILE_SIZE) {
            throw new BusinessException(Constants.CODE_CAPACITY_EXCEEDED, "too many rows to reconcile safely: db=" + products.size() + ", es=" + esCount + ", limit=" + MAX_RECONCILE_SIZE);
        }

        Map<String, ProductDocument> documents = searchService.listAll(MAX_RECONCILE_SIZE);
        Set<String> dbIds = products.stream().map(product -> String.valueOf(product.getId())).collect(Collectors.toSet());

        List<String> missingIds = new ArrayList<>();
        List<String> staleIds = new ArrayList<>();
        List<ProductDocument> needRewrite = new ArrayList<>();
        for (Product product : products) {
            String id = String.valueOf(product.getId());
            ProductDocument document = documents.get(id);
            if (document == null) {
                missingIds.add(id);
                needRewrite.add(toDocument(product));
            } else if (!sameContent(product, document)) {
                staleIds.add(id);
                needRewrite.add(toDocument(product));
            }
        }

        List<String> orphanIds = documents.keySet().stream().filter(id -> !dbIds.contains(id)).toList();

        ConsistencyReport report = new ConsistencyReport();
        report.setDbCount(products.size());
        report.setEsCount((int) esCount);
        report.setMissingIds(missingIds);
        report.setStaleIds(staleIds);
        report.setOrphanIds(orphanIds);
        report.setRepaired(repair);

        if (!repair) {
            return report;
        }

        if (!needRewrite.isEmpty()) {
            searchService.bulkIndex(needRewrite);
        }
        if (!orphanIds.isEmpty()) {
            searchService.bulkDelete(orphanIds);
        }
        report.setRepairedCount(needRewrite.size() + orphanIds.size());
        if (report.getRepairedCount() == 0) {
            return report;
        }
        // 只有真的改动过才刷新和打日志：一来 ES 写入默认 1 秒后才可见，
        // 不刷新的话调用方紧接着复查会看到同一批差异，像是没修好；
        // 二来定时任务按间隔轮询，无差异也打日志会把日志刷成没什么可看的流水。
        searchService.refresh();
        log.warn("reconciled search index: db={}, es={}, missing={}, stale={}, orphan={}, repaired={}", products.size(), esCount, missingIds.size(), staleIds.size(), orphanIds.size(), report.getRepairedCount());
        return report;
    }

    /**
     * 清理索引里数据库已经不存在的文档。
     *
     * <p>与对账不同，这里不需要两边比对，只要「库里没有的 id 一律删」，
     * 所以交给 ES 的 delete_by_query 去做，不受对账那条内存上限的约束：
     * 一旦这里也被上限卡住，索引越脏就越清理不动，等于把唯一的自救通道堵死了。
     *
     * @param products 数据库全量商品
     * @return 清理掉的文档数
     */
    private int removeOrphans(List<Product> products) {
        if (products.isEmpty()) {
            // 库里一条都没有时不去动索引：这种状态八成是数据源出了问题，
            // 照着「保留名单为空」执行会把整个索引清空，代价远大于留下几份幽灵文档
            log.warn("database returns no product, skip orphan cleanup to avoid wiping the index");
            return 0;
        }
        Set<String> dbIds = products.stream().map(product -> String.valueOf(product.getId())).collect(Collectors.toSet());
        return (int) searchService.deleteExcept(dbIds);
    }

    /**
     * 比对库与索引里的同一条数据。所有写入都走同一个 toDocument，
     * 所以两边只要字段对得上就说明没有漂移。
     */
    private boolean sameContent(Product product, ProductDocument document) {
        if (!Objects.equals(product.getName(), document.getName())) {
            return false;
        }
        if (!Objects.equals(product.getCategory(), document.getCategory())) {
            return false;
        }
        if (!samePrice(product.getPrice(), document.getPrice())) {
            return false;
        }
        if (!Objects.equals(product.getStock(), document.getStock())) {
            return false;
        }
        if (!Objects.equals(statusName(product), document.getStatus())) {
            return false;
        }
        if (!Objects.equals(resolveDescription(product), document.getDescription())) {
            return false;
        }
        return Objects.equals(product.getCreateTime(), document.getCreateTime());
    }

    /**
     * 价格比对只能比数值，不能比 equals：库里是 decimal(12,2) 的 599.00，
     * 写进索引变成 double，读回来是 599.0，而 BigDecimal.equals 连小数位数一起比，
     * 用 equals 会对着一堆正常数据报差异。
     *
     * @param dbPrice    库里的价格
     * @param indexPrice 索引里的价格
     * @return 是否相等
     */
    private boolean samePrice(BigDecimal dbPrice, BigDecimal indexPrice) {
        if (dbPrice == null || indexPrice == null) {
            return dbPrice == indexPrice;
        }
        return dbPrice.compareTo(indexPrice) == 0;
    }

    /**
     * 把商品转成索引文档。所有写入路径共用这一个转换，
     * 对账里才敢跳过 description 的比对。
     */
    private ProductDocument toDocument(Product product) {
        ProductDocument document = new ProductDocument();
        document.setId(String.valueOf(product.getId()));
        document.setName(product.getName());
        document.setCategory(product.getCategory());
        document.setDescription(resolveDescription(product));
        document.setPrice(product.getPrice());
        document.setStock(product.getStock());
        document.setStatus(statusName(product));
        document.setSuggest(suggestInputs(product));
        document.setLocation(toLocation(product));
        document.setCreateTime(product.getCreateTime());
        return document;
    }

    /**
     * 详情文本。库里填了详情就用库里的，没填再退回「名称 + 分类」的拼接：
     * 索引里这一列不能为空，否则检索与高亮会整段落空。
     *
     * <p>对账比对详情时也必须走这个方法，否则库里是 null、索引里是拼接值，
     * 会被误判成内容不一致。
     */
    private String resolveDescription(Product product) {
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            return product.getDescription();
        }
        return product.getName() + " " + product.getCategory();
    }

    /**
     * 补全素材。除商品名外把分类也塞进去：补全只支持前缀匹配，
     * 多给一个入口，用户输入分类名也能补全出商品。
     */
    private List<String> suggestInputs(Product product) {
        List<String> inputs = new ArrayList<>();
        inputs.add(product.getName());
        if (product.getCategory() != null && !product.getCategory().isBlank()) {
            inputs.add(product.getCategory());
        }
        return inputs;
    }

    /**
     * 坐标。库里是 longitude 与 latitude 两列，ES 的 geo_point 要求是一个整体字段，
     * 而且顺序是 lat 在前 lon 在后——这个顺序写反了不报错，只会算出完全错误的距离。
     */
    private Map<String, Double> toLocation(Product product) {
        if (product.getLongitude() == null || product.getLatitude() == null) {
            return null;
        }
        Map<String, Double> location = new LinkedHashMap<>();
        location.put("lat", product.getLatitude());
        location.put("lon", product.getLongitude());
        return location;
    }

    /**
     * 取商品状态名，索引里存的是 keyword 字符串。
     */
    private String statusName(Product product) {
        return product.getStatus() == null ? null : product.getStatus().name();
    }

}
