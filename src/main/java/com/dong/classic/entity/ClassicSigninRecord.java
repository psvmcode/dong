package com.dong.classic.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
/**
 * 签到流水。
 *
 * <p>位图回答「某天有没有签到」只要一次位运算，
 * 但位图存在 Redis 里、有保留期，过期或 Redis 故障就查不到了。
 * 这张表是签到的权威记录：可以查历史、可以对账，
 * 最坏情况下还能用它把位图整个重建回来。
 *
 * <p>唯一键 (user_id, sign_date) 与位图的幂等语义一致：
 * 重复签到在两边都不会产生第二条记录。
 */
@Data

public class ClassicSigninRecord {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户标识
     */
    private String userId;

    /**
     * 签到日期
     */
    private LocalDate signDate;

    /**
     * 截至本次签到的连续天数，冗余保存便于直接查询历史
     */
    private Integer continuousDays;

    /**
     * 来源：normal 正常签到 repair 补签
     */
    private String source;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
