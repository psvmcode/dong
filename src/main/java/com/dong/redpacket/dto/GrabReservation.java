package com.dong.redpacket.dto;

import com.dong.redpacket.enums.GrabStatus;

/**
 * 库存预扣结果。
 *
 * <p>itemClaimed 表示份额在数据库里是否已经占位：
 * Redis 路径只弹队列不碰库，留给落库阶段占位；
 * 数据库降级路径必须当场占位，否则并发请求会选中同一份。
 */
public record GrabReservation(GrabStatus status, long amount, int seq, boolean itemClaimed) {

    public static GrabReservation grabbed(long amount, int seq, boolean itemClaimed) {
        return new GrabReservation(GrabStatus.GRABBED, amount, seq, itemClaimed);
    }

    public static GrabReservation of(GrabStatus status) {
        return new GrabReservation(status, 0L, 0, false);
    }

    public boolean grabbed() {
        return status == GrabStatus.GRABBED;
    }

}
