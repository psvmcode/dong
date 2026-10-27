package com.dong.logging.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 历史日志回填请求。
 *
 * <p>回填的是 logback 已经落盘的文件，对应 Filebeat 那一环：
 * 应用启动之前的日志、上一次采集掉过的日志，都靠它补进索引。
 *
 * <p>单次条数必须封顶：一个跑了半年的日志目录是几百万行，
 * 一次全读会把内存和 ES 一起打满。封顶之后靠位点续传多跑几次即可。
 */
@Data
public class LogImportRequest {

    /**
     * 本次最多处理多少行。
     */
    @Min(1)
    @Max(20 * Constants.MAX_BATCH_SIZE)
    private int maxLines = 5 * Constants.MAX_BATCH_SIZE;

}
