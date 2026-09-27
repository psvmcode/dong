package com.dong.search.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 补全请求。prefix 是用户已经输入的内容，走的是前缀匹配而不是全文检索。
 */
public class SuggestQueryRequest {

    /**
     * 已输入的前缀。
     */
    @NotBlank
    @Size(max = 128)
    private String prefix;

    /**
     * 返回条数。
     */
    @Min(1)
    @Max(Constants.MAX_PAGE_SIZE)
    private int size = 10;

    /**
     * 获取已输入的前缀。
     *
     * @return 前缀
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * 设置已输入的前缀。
     *
     * @param prefix 前缀
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 获取返回条数。
     *
     * @return 返回条数
     */
    public int getSize() {
        return size;
    }

    /**
     * 设置返回条数。
     *
     * @param size 返回条数
     */
    public void setSize(int size) {
        this.size = size;
    }

}
