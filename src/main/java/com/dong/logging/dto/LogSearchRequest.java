package com.dong.logging.dto;

import com.dong.common.constant.Constants;
import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 日志检索请求。检索与仪表盘共用它，两边的过滤口径才不会走偏——
 * 「图上看到 20 条 ERROR，点进去却是 15 条」这种事，几乎都是两套条件写歪了。
 *
 * <p>时间范围用毫秒时间戳而不是日期字符串：字符串要带时区才能解析对，
 * 而传错了时区的表现是「数据没少，只是整体平移了八小时」，最难发现。
 * 都不传就是不限时间。
 *
 * <p>级别是 keyword 上的 terms 过滤，所以传进来的是什么就匹配什么，
 * 服务层会统一转大写，避免前端传 info 查不到东西。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogSearchRequest extends PageQuery {

    /**
     * 关键字。走 multi_match，正文权重最高，堆栈次之，追踪 id 也参与。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String keyword;

    /**
     * 级别过滤，如 INFO / WARN / ERROR。多选，空或者不传表示不限。
     */
    @Size(max = 8)
    private List<String> levels;

    /**
     * logger 前缀。走 prefix 查询而不是 term：按包名看一类日志是日志检索最常用的 narrowing 手段，
     * 让用户填完整类名不现实。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String loggerPrefix;

    /**
     * 线程名，精确匹配。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String thread;

    /**
     * 链路追踪 id，精确匹配。一次请求的全部日志靠它串起来。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String traceId;

    /**
     * 主机名，精确匹配。多实例时用来只看某一台。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String host;

    /**
     * 起始时间，含边界，毫秒时间戳。
     */
    private Long fromMillis;

    /**
     * 结束时间，含边界，毫秒时间戳。
     */
    private Long toMillis;

}
