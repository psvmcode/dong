package com.dong.common.util;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;

public final class Base62Utils {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final int BASE = ALPHABET.length;

    private Base62Utils() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "value must not be negative");
        }
        if (value == 0) {
            return String.valueOf(ALPHABET[0]);
        }
        StringBuilder builder = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            builder.append(ALPHABET[(int) (remaining % BASE)]);
            remaining = remaining / BASE;
        }
        return builder.reverse().toString();
    }

    public static long decode(String value) {
        long result = 0L;
        for (int i = 0; i < value.length(); i++) {
            result = result * BASE + indexOf(value.charAt(i));
        }
        return result;
    }

    private static int indexOf(char symbol) {
        for (int i = 0; i < ALPHABET.length; i++) {
            if (ALPHABET[i] == symbol) {
                return i;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "illegal base62 character " + symbol);
    }

}
