package com.dong.common.result;

import com.dong.common.constant.Constants;

/**
 * 分页请求参数。
 */
public class PageRequest {

    /**
     * 当前页码。
     */
    private int pageNum = Constants.DEFAULT_PAGE_NUM;

    /**
     * 每页大小。
     */
    private int pageSize = Constants.DEFAULT_PAGE_SIZE;

    /**
     * 创建分页请求。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页大小
     * @return 分页请求
     */
    public static PageRequest of(int pageNum, int pageSize) {
        PageRequest request = new PageRequest();
        request.setPageNum(pageNum < 1 ? Constants.DEFAULT_PAGE_NUM : pageNum);
        request.setPageSize(Math.min(pageSize < 1 ? Constants.DEFAULT_PAGE_SIZE : pageSize, Constants.MAX_PAGE_SIZE));
        return request;
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

    /**
     * 获取数据库偏移量。
     *
     * @return 偏移量
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

}
