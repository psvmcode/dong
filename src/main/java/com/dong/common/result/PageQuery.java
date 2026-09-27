package com.dong.common.result;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 分页查询参数基类。所有「带条件 + 分页」的查询请求继承它，
 * 避免每个查询 DTO 都把 pageNum、pageSize 和它们的校验重写一遍。
 *
 * <p>上限直接引用 Constants：页码不加限制时，
 * 偏移量 (pageNum - 1) * pageSize 会溢出成负数，把 limit 语句变成语法错误。
 * 另外接口全部改成 POST 之后，分页参数也随请求体一起提交，
 * 不再走 query string，这里因此带上默认值，不传也能查出第一页。
 */
public class PageQuery {

    /**
     * 当前页码。
     */
    @Min(1)
    @Max(Constants.MAX_PAGE_NUM)
    private int pageNum = Constants.DEFAULT_PAGE_NUM;

    /**
     * 每页大小。
     */
    @Min(1)
    @Max(Constants.MAX_PAGE_SIZE)
    private int pageSize = Constants.DEFAULT_PAGE_SIZE;

    /**
     * 转换为服务层使用的分页请求。
     *
     * @return 分页请求
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
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
