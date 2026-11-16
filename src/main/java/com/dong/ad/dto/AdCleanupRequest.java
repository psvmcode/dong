package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

/**
 * 归档清理请求。默认清理七天前的在线数据，保留最近数据供对账与排查。
 */
@Data
public class AdCleanupRequest {

    /**
     * 归属日期上界（不含），不传取七天前。
     */
    private LocalDate beforeDate;

    /**
     * 单批处理条数。
     */
    @Min(1)
    @Max(Constants.MAX_BATCH_SIZE)
    private int limit = 200;

}
