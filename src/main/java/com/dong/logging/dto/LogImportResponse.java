package com.dong.logging.dto;

import lombok.Data;

/**
 * 历史日志回填结果。
 */
@Data
public class LogImportResponse {

    /**
     * 本次处理到的文件数。
     */
    private int files;

    /**
     * 本次解析出的日志条数。
     *
     * <p>小于扫描的文件行数：堆栈的每一行都会合并进它所属的那条日志。
     */
    private long parsed;

    /**
     * 本次写入 ES 的条数。
     */
    private long indexed;

    /**
     * 被跳过的条数：超出保留窗口的历史日志。
     *
     * <p>回填不是归档。超出保留窗口的数据进了索引，
     * 下一次清理任务会立刻把它删掉，白白占一轮磁盘和 IO。
     */
    private long skipped;

}
