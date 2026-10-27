package com.dong.logging.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 生成样本日志的请求。
 *
 * <p>存在的理由很实际：日志检索页面在没有流量的时候是空的，
 * 空页面上既看不出直方图对不对，也看不出级别筛选有没有生效。
 * 与其等真实故障，不如先把数据造出来，把「能不能查、能不能看」验证掉。
 */
@Data
public class LogGenerateRequest {

    /**
     * 生成条数。
     */
    @Min(1)
    @Max(Constants.MAX_BATCH_SIZE)
    private int count = 50;

    /**
     * 是否带上异常堆栈。带上才能验证堆栈字段的检索与展示。
     */
    private boolean withException = true;

    /**
     * 是否模拟链路：每若干条共用一个追踪 id，
     * 用来验证「按 traceId 把一次请求的日志串起来」这条最有用的检索路径。
     */
    private boolean withTrace = true;

}
