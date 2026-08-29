package com.dong.lab.common.result;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class PageResult<T> {

    private long total;

    private List<T> list;

    private int pageNum;

    private int pageSize;

    public static <T> PageResult<T> empty(PageRequest request) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(0L);
        result.setList(Collections.emptyList());
        result.setPageNum(request.getPageNum());
        result.setPageSize(request.getPageSize());
        return result;
    }

    public static <T> PageResult<T> of(List<T> list, long total, PageRequest request) {
        PageResult<T> result = new PageResult<>();
        result.setList(list == null ? Collections.emptyList() : list);
        result.setTotal(total);
        result.setPageNum(request.getPageNum());
        result.setPageSize(request.getPageSize());
        return result;
    }

    public <U> PageResult<U> map(Function<? super T, ? extends U> mapper) {
        PageResult<U> result = new PageResult<>();
        result.setTotal(this.total);
        result.setPageNum(this.pageNum);
        result.setPageSize(this.pageSize);
        result.setList(this.list.stream().<U>map(mapper::apply).toList());
        return result;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

}
