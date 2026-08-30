package com.dong.lab.classic.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

/**
 * 用户签到。基于 Bitmap，每个用户每月只占极少存储，
 * 一年下来一个用户也就几百字节，这是相比记录表的最大优势。
 */
public interface SignInService {

    /**
     * 签到，返回 false 表示当天已签过。
     */
    boolean signIn(String userId, LocalDate date);

    /**
     * 查询指定日期是否已签到。
     */
    boolean hasSigned(String userId, LocalDate date);

    /**
     * 统计当月累计签到天数。
     */
    long countInMonth(String userId, YearMonth month);

    /**
     * 查询连续签到天数，从指定日期往前推算，中断即止。
     */
    long continuousDays(String userId, LocalDate today);

    /**
     * 查询当月签到日历。
     */
    Map<String, Boolean> monthCalendar(String userId, YearMonth month);

}
