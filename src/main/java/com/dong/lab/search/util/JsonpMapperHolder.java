package com.dong.lab.search.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dong.lab.common.util.JsonUtils;

public final class JsonpMapperHolder {

    public static final ObjectMapper MAPPER = JsonUtils.mapper();

    private JsonpMapperHolder() {
    }

}
