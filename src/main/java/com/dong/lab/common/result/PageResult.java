package com.dong.lab.common.result;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    /**
     * 总记录数。
     */
    private long total;

    /**
     * 当前页数据列表。
     */
    private List<T> list;

    /**
     * 当前页码。
     */
    private int pageNum;

    /**
     * 每页大小。
     */
    private int pageSize;

    /**
     * 创建空分页结果。
     *
     * @param request 分页请求
     * @return 空分页结果
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
     * 创建分页结果。
     *
     * @param list    当前页数据列表
     * @param total   总记录数
     * @param request 分页请求
     * @return 分页结果
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
     * 转换列表元素类型。
     *
     * @param mapper 类型转换函数
     * @param <U>    目标元素类型
     * @return 转换后的分页结果
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
     * 获取总记录数。
     *
     * @return 总记录数
     */
    public long getTotal() {
        return total;
    }

    /**
     * 设置总记录数。
     *
     * @param total 总记录数
     */
    public void setTotal(long total) {
        this.total = total;
    }

    /**
     * 获取当前页数据列表。
     *
     * @return 当前页数据列表
     */
    public List<T> getList() {
        return list;
    }

    /**
     * 设置当前页数据列表。
     *
     * @param list 当前页数据列表
     */
    public void setList(List<T> list) {
        this.list = list;
    }

    /**
     * 获取当前页码。
     *
     * @return 当前页码
     */
    public int getPageNum() {
        return pageNum;
    }

    /**
     * 设置当前页码。
     *
     * @param pageNum 当前页码
     */
    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    /**
     * 获取每页大小。
     *
     * @return 每页大小
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * 设置每页大小。
     *
     * @param pageSize 每页大小
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

}
