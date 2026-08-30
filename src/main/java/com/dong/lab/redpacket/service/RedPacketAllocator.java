package com.dong.lab.redpacket.service;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RedPacketAllocator {

    private static final long MIN_AMOUNT = 1L;

    private RedPacketAllocator() {
    }

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
