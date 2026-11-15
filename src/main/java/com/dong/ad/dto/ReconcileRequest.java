package com.dong.ad.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 手动触发对账请求。不传日期默认对今天，
 * 传日期用于重跑历史某一天，重跑必须是幂等的。
 */
@Data
public class ReconcileRequest {

    /**
     * 对账日期，不传取当天。
     */
    private LocalDate statDate;

    /**
     * 是否补投超时待计费事件，默认补投。关掉用于只想重算汇总的场景。
     */
    private Boolean resendPending = true;

}
