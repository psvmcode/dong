package com.dong.lab.search.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IndexNameResolver {

    private final String prefix;

    public IndexNameResolver(@Value("${lab.elasticsearch.index-prefix:dong_lab}") String prefix) {
        this.prefix = prefix;
    }

    public String resolve(String name) {
        return prefix + "_" + name;
    }

}
