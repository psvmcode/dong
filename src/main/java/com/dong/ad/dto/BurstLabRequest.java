package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 并发计费对照实验请求。
 *
 * <p>线程数与单线程循环数都必须封顶，且总任务量另受 MAX_BATCH_SIZE 约束：
 * 只限制其中一个，另一个传大值照样能打出十万次扣费把数据库连接池占满。
 */
@Data
public class BurstLabRequest {

    /**
     * 实验计划 id，建议用一条专门新建的计划，别污染正式数据。
     */
    @NotNull
    @Positive
    private Long campaignId;

    /**
     * 并发线程数。
     */
    @Min(1)
    @Max(Constants.MAX_THREADS)
    private int threads = 16;

    /**
     * 每线程循环次数。
     */
    @Min(1)
    @Max(Constants.MAX_LOOPS)
    private int loopsPerThread = 10;

    /**
     * 预算闸门路径：redis 或 db，不传取当前配置。
     */
    @Size(max = 16)
    private String mode;

    /**
     * 触达用户 id 起始值，各任务在它上面递增，模拟不同用户。
     */
    @NotNull
    @Positive
    private Long userId = 9_000_000L;

}
