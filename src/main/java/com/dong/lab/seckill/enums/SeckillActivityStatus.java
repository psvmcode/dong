package com.dong.lab.seckill.enums;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 秒杀活动状态。控制活动从草稿到结束的完整生命周期。
 */
@Getter
@AllArgsConstructor

public enum SeckillActivityStatus {

    /**
     * 草稿，活动尚未发布，仅后台可见。
     */
    DRAFT(0),

    /**
     * 已预热，库存等数据已准备就绪，等待上线。
     */
    PREPARED(1),

    /**
     * 上线中，用户可正常参与秒杀。
     */
    ONLINE(2),

    /**
     * 已结束，活动到达截止时间，不再接受新订单。
     */
    FINISHED(3);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取秒杀活动状态枚举。
     *
     * @param code 状态编码
     * @return 秒杀活动状态枚举
     */
    public static SeckillActivityStatus of(int code) {
        for (SeckillActivityStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown seckill activity status " + code);
    }

}
