package com.dong.lab.framework.cache;

public final class CacheEmpty {

    public static final CacheEmpty INSTANCE = new CacheEmpty();

    private CacheEmpty() {
    }

    @Override
    public String toString() {
        return "CacheEmpty";
    }

}
