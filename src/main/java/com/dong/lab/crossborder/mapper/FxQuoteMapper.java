package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.FxQuote;
import com.dong.lab.crossborder.enums.FxQuoteStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
/**
 * FxQuoteMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface FxQuoteMapper {

    /**
     * 按 QuoteNo 查询记录。
     */
    FxQuote selectByQuoteNo(String quoteNo);

    /**
     * 按 PairAndStatus 查询记录。
     */
    List<FxQuote> selectByPairAndStatus(@Param("currencyPair") String currencyPair,
                                        @Param("status") FxQuoteStatus status,
                                        @Param("limit") int limit);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(FxQuote quote);

    /**
     * 锁定报价。带上状态条件形成乐观锁，并发锁定同一个报价时只有一个能成功。
     * 有效期用数据库时间比对，避免应用时钟漂移导致过期报价仍被锁定。
     */
    int lock(@Param("quoteNo") String quoteNo,
             @Param("remittanceNo") String remittanceNo,
             @Param("lockedRate") java.math.BigDecimal lockedRate);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("quoteNo") String quoteNo, @Param("status") FxQuoteStatus status);

    /**
     * 批量标记过期报价，由定时任务调用。
     * 这里不用 limit 是因为 MySQL 的 update 语句不支持 limit 占位符。
     */
    int expireOverdue(@Param("now") LocalDateTime now);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
