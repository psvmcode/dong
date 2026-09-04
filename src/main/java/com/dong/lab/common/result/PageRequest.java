package com.dong.lab.common.result;

import com.dong.lab.common.constant.Constants;

/**
 * PageRequest。
 */
public class PageRequest {

    /**
     * DEFAULT_PAGE_NUM。
     */
    private int pageNum = Constants.DEFAULT_PAGE_NUM;

    /**
     * DEFAULT_PAGE_SIZE。
     */
    private int pageSize = Constants.DEFAULT_PAGE_SIZE;

    /**
     * of。
     */
    public static PageRequest of(int pageNum, int pageSize) {
        PageRequest request = new PageRequest();
        request.setPageNum(pageNum < 1 ? Constants.DEFAULT_PAGE_NUM : pageNum);
        request.setPageSize(Math.min(pageSize < 1 ? Constants.DEFAULT_PAGE_SIZE : pageSize, Constants.MAX_PAGE_SIZE));
        return request;
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

    /**
     * getOffset。
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

}
