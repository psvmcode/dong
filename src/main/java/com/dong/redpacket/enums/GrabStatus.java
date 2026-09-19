package com.dong.redpacket.enums;

/**
 * 抢红包的库存判定结果。调用方必须逐个区分：
 * 抢完与重复抢都是"没抢到"，但含义完全不同，混在一起会让用户看到错误提示；
 * 未预热与中间件不可用则需要触发不同的自救动作，而不是直接失败。
 */
public enum GrabStatus {

    /**
     * 抢到了，携带金额与份额序号。
     */
    GRABBED,

    /**
     * 已抢完，库存为零。
     */
    SOLD_OUT,

    /**
     * 该用户已经抢过，不可重复领取。
     */
    DUPLICATED,

    /**
     * 库存未预热或计数与队列不一致，需要先重建。
     */
    NOT_PREPARED,

    /**
     * Redis 不可用，需要降级到数据库。
     */
    UNAVAILABLE

}
