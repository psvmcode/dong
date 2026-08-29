package com.dong.lab.seckill.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeductStatus {

    SUCCESS(0),

    NOT_PREPARED(-1),

    SOLD_OUT(-2),

    DUPLICATED(-3);

    private final int code;

    public static DeductStatus fromResult(int result) {
        return switch (result) {
            case -1 -> NOT_PREPARED;
            case -2 -> SOLD_OUT;
            case -3 -> DUPLICATED;
            default -> SUCCESS;
        };
    }

}
