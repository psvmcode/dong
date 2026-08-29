package com.dong.lab.framework.limiter;

public enum RateLimitAlgorithm {

    FIXED_WINDOW,

    SLIDING_WINDOW,

    TOKEN_BUCKET,

    LEAKY_BUCKET
}
