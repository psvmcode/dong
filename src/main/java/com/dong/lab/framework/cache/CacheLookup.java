package com.dong.lab.framework.cache;

public sealed interface CacheLookup<T> {

    record Hit<T>(T value) implements CacheLookup<T> {
    }

    record Empty<T>() implements CacheLookup<T> {
    }

    record Miss<T>() implements CacheLookup<T> {
    }

}
