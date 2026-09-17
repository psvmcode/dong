package com.dong.search.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
/**
 * 商品搜索文档。与 MySQL 商品表对应，是 Elasticsearch 里那条可检索的数据。
 *
 * <p>它和数据库实体不是一回事，两边刻意不对称：
 * <ul>
 *   <li>{@code description} 是派生字段，由 name 和 category 拼出来，库里没有对应列。
 *       拼出来的原因是检索要跨字段命中：用户输入「外设 键盘」时，
 *       只有 name 字段能命中「键盘」，而 category 单独成字段又便于聚合，拼一份是最省事的做法。</li>
 *   <li>{@code suggest} 是 completion 类型的补全字段，库里也不存在，
 *       内容由 name 派生（见同步服务的转换逻辑）。补全和检索用的数据结构不同，
 *       completion 走的是内存里的 FST，不是倒排索引，所以必须单独存一份。</li>
 *   <li>{@code location} 存的是 {"lat":..,"lon":..}，与库里 longitude/latitude 两列对应，
 *       但 ES 要求 geo_point 是一个整体字段，不能拆成两列。</li>
 * </ul>
 *
 * <p>所有字段类型都在启动时由映射显式声明，不能靠 ES 动态推断：
 * 字符串默认会被推断成 text，而 text 字段做 terms 聚合会直接报错。
 */
@Data
public class ProductDocument {

    /**
     * 文档 id，与 MySQL 主键对应
     */
    private String id;

    /**
     * 商品名称，ik_max_word 索引
     */
    private String name;

    /**
     * 分类，keyword 类型支持 terms 聚合
     */
    private String category;

    /**
     * 检索用描述，由 name 与 category 拼接而来，ik_max_word 索引
     */
    private String description;

    /**
     * 商品价格，索引里是 double，比库里的 decimal(12,2) 精度松，
     * 所以回读比对价格时要用 compareTo 而不是 equals
     */
    private BigDecimal price;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 商品状态名，索引里是 keyword 字符串，库里是 tinyint 枚举
     */
    private String status;

    /**
     * 前缀补全字段。completion 类型，只服务于自动补全，
     * 不能当普通字段检索：它不建倒排，走的是专门的 FST 结构。
     */
    private List<String> suggest;

    /**
     * 门店坐标，geo_point 类型，写成 {"lat":纬度,"lon":经度}。
     * 商品没填经纬度时这里是 null，ES 会跳过该文档，不会参与距离计算。
     */
    private Map<String, Double> location;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
