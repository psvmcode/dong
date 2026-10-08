package com.dong.upload.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已接收的分片记录。
 *
 * <p>uk_upload_chunk 保证「同一任务下的同一分片只有一条」：
 * 断点续传时前端可能重复发送同一个分片（比如上一轮发出去了但没收到响应），
 * 有了这条唯一键，重复上传走覆盖而不是新增，进度统计才不会重复计数。
 *
 * <p>特大文件会切出上万条分片，每条一行。这里刻意不记录分片的二进制内容，
 * 只记元信息——内容要么落盘由 storage 管，要么在开关关闭时压根不存。
 */
@Data
public class UploadChunk {

    /**
     * 主键。
     */
    private Long id;

    /**
     * 上传任务号。
     */
    private String uploadId;

    /**
     * 分片下标，从 0 开始。
     */
    private Integer chunkIndex;

    /**
     * 分片实际字节数。
     */
    private Long chunkSize;

    /**
     * 分片指纹，重复上传同一片时可用于校验。
     */
    private String chunkHash;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

}
