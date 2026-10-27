package com.dong.logging.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 历史日志回填的位点。存在 ES 里，而不是本地文件。
 *
 * <p>位点是「跨重启、跨实例共享」的状态：放本地文件意味着换台机器就重导一遍，
 * 放内存意味着重启就重导一遍。ES 本来就是这一环唯一依赖的外部存储，
 * 多存一份状态没有额外成本。
 *
 * <p>按行数记位点而不是按字节：字节偏移在多字节字符和换行符宽度上都会漂移，
 * 一旦漂了就会从半行开始解析，产出一条谁也看不懂的日志。
 * 行数没有这个问题，代价只是不能断点到字节级——对日志回填毫无影响。
 */
@Data
public class LogImportState {

    /**
     * 文件路径，同时作为文档 id。
     */
    private String path;

    /**
     * 已处理的行数。
     */
    private long lines;

    /**
     * 记位点时文件的大小。用来识别文件被截断或轮转过——
     * 变小了就说明现在这个文件已经不是当初那个，必须从头再来。
     */
    private long size;

    /**
     * 是否已经读到文件末尾。压缩文件不会增长，导完一次就永远跳过。
     */
    private boolean done;

    /**
     * 更新时间。
     */
    private Instant updatedAt;

}
