package com.dong.lab.redpacket.service;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 红包金额分配。核心是二倍均值法：
 * 每次在 1 到 2 倍均值减 1 之间随机取值，既保证每人期望相等，又保留随机惊喜。
 * 关键是必须为剩余人数预留最低金额，否则前面的人可能把钱分完，后面的人拿到零。
 */
public class RedPacketAllocator {

    private static final long MIN_AMOUNT = 1L;

    private RedPacketAllocator() {
    }

    /**
     * 随机分配。金额以分为单位参与运算，全程用整数避免浮点误差。
     * 最后一份直接取剩余金额，因此总和一定精确等于 totalAmount。
     */
    public static List<Long> allocate(long totalAmount, int count) {
        if (count <= 0) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "count must be positive");
        }
        if (totalAmount < (long) count * MIN_AMOUNT) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "total amount must leave at least one cent for each grabber");
        }

        long remain = totalAmount;
        int remainCount = count;
        List<Long> amounts = new ArrayList<>(count);
        for (int i = 0; i < count - 1; i++) {
            long average = remain / remainCount;
            long upper = Math.max(MIN_AMOUNT, average * 2 - 1);
            long amount = ThreadLocalRandom.current().nextLong(MIN_AMOUNT, upper + 1);
            long reserveForOthers = (long) (remainCount - 1) * MIN_AMOUNT;
            amount = Math.min(amount, remain - reserveForOthers);
            amount = Math.max(amount, MIN_AMOUNT);
            amounts.add(amount);
            remain = remain - amount;
            remainCount--;
        }

        amounts.add(remain);
        Collections.shuffle(amounts);
        return amounts;
    }

    /**
     * 均分。除不尽的余数补在最后一份上，
     * 这个细节保证总额精确守恒，不会因整除丢掉零头。
     */
    public static List<Long> allocateFixed(long totalAmount, int count) {
        if (count <= 0) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "count must be positive");
        }
        long each = totalAmount / count;
        List<Long> amounts = new ArrayList<>(count);
        long distributed = 0L;
        for (int i = 0; i < count - 1; i++) {
            amounts.add(each);
            distributed = distributed + each;
        }
        amounts.add(totalAmount - distributed);
        return amounts;
    }

}
