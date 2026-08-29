package com.dong.lab.common.result;

import com.dong.lab.common.constant.Constants;

public class PageRequest {

    private int pageNum = Constants.DEFAULT_PAGE_NUM;

    private int pageSize = Constants.DEFAULT_PAGE_SIZE;

    public static PageRequest of(int pageNum, int pageSize) {
        PageRequest request = new PageRequest();
        request.setPageNum(pageNum < 1 ? Constants.DEFAULT_PAGE_NUM : pageNum);
        request.setPageSize(Math.min(pageSize < 1 ? Constants.DEFAULT_PAGE_SIZE : pageSize, Constants.MAX_PAGE_SIZE));
        return request;
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

    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }

}
