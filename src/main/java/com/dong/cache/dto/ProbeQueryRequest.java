package com.dong.cache.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 缓存探测请求。key 是要探测的缓存键，
 * value 是缓存没有时回源返回的兜底值，不传就用默认字符串。
 */
public class ProbeQueryRequest {

    /**
     * 缓存键。
     */
    @NotBlank
    @Size(max = 128)
    private String key;

    /**
     * 回源时写入的值。
     */
    @NotBlank
    @Size(max = Constants.MAX_TEXT_LENGTH)
    private String value = "probe-value";

    /**
     * 获取缓存键。
     *
     * @return 缓存键
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置缓存键。
     *
     * @param key 缓存键
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * 获取回源时写入的值。
     *
     * @return 回源值
     */
    public String getValue() {
        return value;
    }

    /**
     * 设置回源时写入的值。
     *
     * @param value 回源值
     */
    public void setValue(String value) {
        this.value = value;
    }

}
