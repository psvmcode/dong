package com.dong.seckill.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 秒杀库存扣减结果。通过返回码区分扣减是否成功及失败原因。
 */
@Getter
@AllArgsConstructor

public enum DeductStatus {

    /**
     * 扣减成功。
     */
    SUCCESS(0),

    /**
     * 活动未准备，缓存预热或活动状态尚未就绪。
     */
    NOT_PREPARED(-1),

    /**
     * 已售罄，没有剩余库存可供扣减。
     */
    SOLD_OUT(-2),

    /**
     * 重复下单，同一用户已参加过本次秒杀。
     */
    DUPLICATED(-3);

    /**
     * 扣减结果编码，负数表示各类失败原因。
     */
    private final int code;

    /**
     * 根据扣减结果码获取对应的扣减状态枚举。
     *
     * @param result 扣减结果码
     * @return 扣减状态枚举
     */
    public static DeductStatus fromResult(int result) {
        return switch (result) {
            case -1 -> NOT_PREPARED;
            case -2 -> SOLD_OUT;
            case -3 -> DUPLICATED;
            default -> SUCCESS;
        };
    }

}
