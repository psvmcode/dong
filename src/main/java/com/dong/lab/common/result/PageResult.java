package com.dong.lab.common.result;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * PageResult<T>。
 */
public class PageResult<T> {

    /**
     * total。
     */
    private long total;

    /**
     * list。
     */
    private List<T> list;

    /**
     * pageNum。
     */
    private int pageNum;

    /**
     * pageSize。
     */
    private int pageSize;

    /**
     * empty。
     */
    public static <T> PageResult<T> empty(PageRequest request) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(0L);
        result.setList(Collections.emptyList());
        result.setPageNum(request.getPageNum());
        result.setPageSize(request.getPageSize());
        return result;
    }

    /**
     * of。
     */
    public static <T> PageResult<T> of(List<T> list, long total, PageRequest request) {
        PageResult<T> result = new PageResult<>();
        result.setList(list == null ? Collections.emptyList() : list);
        result.setTotal(total);
        result.setPageNum(request.getPageNum());
        result.setPageSize(request.getPageSize());
        return result;
    }

    /**
     * map。
     */
    public <U> PageResult<U> map(Function<? super T, ? extends U> mapper) {
        PageResult<U> result = new PageResult<>();
        result.setTotal(this.total);
        result.setPageNum(this.pageNum);
        result.setPageSize(this.pageSize);
        result.setList(this.list.stream().<U>map(mapper::apply).toList());
        return result;
    }

    /**
     * getTotal。
     */
    public long getTotal() {
        return total;
    }

    /**
     * setTotal。
     */
    public void setTotal(long total) {
        this.total = total;
    }

    /**
     * getList。
     */
    public List<T> getList() {
        return list;
    }

    /**
     * setList。
     */
    public void setList(List<T> list) {
        this.list = list;
    }

    /**
     * getPageNum。
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * setPageNum。
     */
    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * getPageSize。
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * setPageSize。
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

}
