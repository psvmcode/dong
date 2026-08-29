package com.dong.lab.common.constant;

public final class Constants {

    public static final int CODE_SUCCESS = 0;

    public static final int CODE_PARAM_INVALID = 1000;

    public static final int CODE_DATA_NOT_FOUND = 1001;

    public static final int CODE_OPERATION_CONFLICT = 1002;

    public static final int CODE_TOO_MANY_REQUESTS = 1003;

    public static final int CODE_MIDDLEWARE_DISABLED = 1004;

    public static final int CODE_DEPENDENCY_UNAVAILABLE = 1005;

    public static final int CODE_IDEMPOTENT_REJECTED = 1006;

    public static final int CODE_INTERNAL_ERROR = 5000;

    public static final String MESSAGE_SUCCESS = "success";

    public static final String MESSAGE_PARAM_INVALID = "invalid parameter";

    public static final String MESSAGE_DATA_NOT_FOUND = "data not found";

    public static final String MESSAGE_OPERATION_CONFLICT = "operation conflict, please retry";

    public static final String MESSAGE_TOO_MANY_REQUESTS = "too many requests";

    public static final String MESSAGE_MIDDLEWARE_DISABLED = "middleware is disabled, turn it on in application.yml";

    public static final String MESSAGE_DEPENDENCY_UNAVAILABLE = "downstream dependency is unavailable";

    public static final String MESSAGE_IDEMPOTENT_REJECTED = "duplicate request rejected";

    public static final String MESSAGE_INTERNAL_ERROR = "internal server error";

    public static final String REDIS_PREFIX = "lab";

    public static final String SEPARATOR = ":";

    public static final int DEFAULT_PAGE_NUM = 1;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 200;

    private Constants() {
    }

}
