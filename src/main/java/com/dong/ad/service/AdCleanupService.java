package com.dong.ad.service;

import java.time.LocalDate;

/**
 * 在线数据生命周期管理。事件与流水带幂等唯一键，天然只增不减，
 * 不清理会让在线表越来越慢，最终拖垮召回与计费。
 */
public interface AdCleanupService {

    /**
     * 把指定日期之前的在线流水搬到归档表，并只删除已确认归档的行。
     *
     * @param beforeDate 归属日期上界（不含）
     * @param limit      单批最大条数
     * @return 实际归档并删除的条数
     */
    int archiveExpiredCharge(LocalDate beforeDate, int limit);

    /**
     * 删除指定日期之前的过期事件。仍待计费的事件保留，避免清掉补偿线索。
     *
     * @param beforeDate 创建日期上界（不含）
     * @param limit      单批最大条数
     * @return 删除条数
     */
    int deleteExpiredEvents(LocalDate beforeDate, int limit);

}
