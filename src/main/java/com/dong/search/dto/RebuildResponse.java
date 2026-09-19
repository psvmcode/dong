package com.dong.search.dto;

import lombok.Data;

/**
 * 索引重建结果。
 *
 * <p>把别名前后指向的索引都返回出来，是为了让调用方能确认「切过去了」：
 * 重建这种操作一旦出问题，最怕的是别名没切、数据却搬了一半，
 * 只返回一个成功状态码看不出这种半吊子状态。
 */
@Data
public class RebuildResponse {

    /**
     * 对外的别名，全程没有变过。
     */
    private String alias;

    /**
     * 重建前别名指向的索引，已被删除。
     */
    private String fromIndex;

    /**
     * 重建后别名指向的索引。
     */
    private String toIndex;

    /**
     * 实际搬过去的文档数。和库里的商品数对不上就说明丢数据了。
     */
    private long movedDocs;

}
