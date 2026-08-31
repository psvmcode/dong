package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.FxQuote;
import com.dong.lab.crossborder.enums.FxQuoteStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FxQuoteMapper {

    FxQuote selectByQuoteNo(String quoteNo);

    List<FxQuote> selectByPairAndStatus(@Param("currencyPair") String currencyPair,
                                        @Param("status") FxQuoteStatus status,
                                        @Param("limit") int limit);

    int insert(FxQuote quote);

    /**
     * 锁定报价。带上状态条件形成乐观锁，并发锁定同一个报价时只有一个能成功。
     * 有效期用数据库时间比对，避免应用时钟漂移导致过期报价仍被锁定。
     */
    int lock(@Param("quoteNo") String quoteNo,
             @Param("remittanceNo") String remittanceNo,
             @Param("lockedRate") java.math.BigDecimal lockedRate);

    int updateStatus(@Param("quoteNo") String quoteNo, @Param("status") FxQuoteStatus status);

    /**
     * 批量标记过期报价，由定时任务调用。
     * 这里不用 limit 是因为 MySQL 的 update 语句不支持 limit 占位符。
     */
    int expireOverdue(@Param("now") LocalDateTime now);

    int clearAll();

}
