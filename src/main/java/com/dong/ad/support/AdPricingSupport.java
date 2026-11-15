package com.dong.ad.support;

import com.dong.ad.enums.AdBidType;
import com.dong.ad.enums.AdEventType;
import org.springframework.stereotype.Component;

/**
 * 竞价与计费计算。全部用 long 的分做运算，不引入浮点：
 * 金额一旦经过 double，日终对账就会出现一分钱的差额，且无法解释是哪一笔丢的。
 *
 * <p>采用广义二价（GSP）：按 bid * pctr 排序，胜出者按「刚好压过第二名」的价格成交，
 * 没有第二名时按保留价成交。成交价由服务端算好签进令牌，客户端无法改写。
 */
@Component
public class AdPricingSupport {

    /**
     * pctr 的万分比基数。
     */
    private static final int PCTR_BASE = 10_000;

    /**
     * 计算竞价值。
     *
     * @param bidCents 出价，单位分
     * @param pctr     预估点击率，万分比
     * @return 竞价值，至少 1
     */
    public long score(long bidCents, int pctr) {
        return Math.max(1L, bidCents * Math.max(1, pctr) / PCTR_BASE);
    }

    /**
     * 计算广义二价成交价。
     *
     * @param winnerBidCents 胜出者出价，单位分
     * @param secondScore    第二名竞价值，没有第二名传 0
     * @param winnerPctr     胜出者预估点击率，万分比
     * @param floorCents     保留价，单位分
     * @return 成交价，单位分
     */
    public long clearing(long winnerBidCents, long secondScore, int winnerPctr, long floorCents) {
        long floor = Math.max(1L, floorCents);
        if (secondScore <= 0L) {
            return Math.min(winnerBidCents, Math.max(floor, 1L));
        }
        long secondPrice = secondScore * PCTR_BASE / Math.max(1, winnerPctr) + 1L;
        return Math.min(winnerBidCents, Math.max(floor, secondPrice));
    }

    /**
     * 计算本笔计费金额。CPM 是每千次曝光价格，单次事件按除数摊销成一条流水。
     *
     * <p>真实平台按累计曝光达到千次才结算一次，这里为保持「一条事件一条流水」
     * 便于幂等与对账，做成单次摊销，误差以流水为准收敛，不会凭空多扣钱。
     *
     * @param clearingCents 成交价，单位分
     * @param bidType       计费类型
     * @param cpmDivisor    CPM 除数
     * @return 本笔计费金额，单位分
     */
    public long charge(long clearingCents, AdBidType bidType, int cpmDivisor) {
        if (bidType == AdBidType.CPM) {
            return Math.max(1L, clearingCents / Math.max(1, cpmDivisor));
        }
        return Math.max(1L, clearingCents);
    }

    /**
     * 判断该事件是否触发扣费。CPC 只在点击扣，CPM 只在曝光扣，
     * 另一类事件仍然进报表，只是不产生金额。
     *
     * @param eventType 事件类型
     * @param bidType   计费类型
     * @return 是否扣费
     */
    public boolean chargeable(AdEventType eventType, AdBidType bidType) {
        return (eventType == AdEventType.CLICK && bidType == AdBidType.CPC)
                || (eventType == AdEventType.IMPRESSION && bidType == AdBidType.CPM);
    }

}
