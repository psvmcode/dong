package com.dong.upload.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分片上传任务。
 *
 * <p>断点续传的全部状态都落在这张表里：任务号、总分片数、指纹、以及当前进度。
 * 前端刷新页面或者断网重连之后，靠 upload_id 把「已经收过哪些分片」问出来，
 * 只补传缺失的那些。
 *
 * <p>uk_hash_size 这条唯一键是秒传与抽样指纹的联合约束：
 * 抽样指纹（只算首尾加中间几块）本身有碰撞风险，必须再带上文件大小一起判定，
 * 否则两个不同的大文件可能被误判成同一个而跳过上传。
 */
@Data
public class UploadTask {

    /**
     * 主键。
     */
    private Long id;

    /**
     * 上传任务号，前端据此续传。
     */
    private String uploadId;

    /**
     * 原始文件名。
     */
    private String fileName;

    /**
     * 文件总大小，单位字节。
     */
    private Long fileSize;

    /**
     * 分片大小，默认 5MB。
     */
    private Integer chunkSize;

    /**
     * 总分片数。
     */
    private Integer totalChunks;

    /**
     * 文件指纹。
     */
    private String fileHash;

    /**
     * 指纹计算方式：full 全量、sample 抽样。
     */
    private String hashMode;

    /**
     * 状态：0 上传中、1 已完成、2 已取消。
     */
    private Integer status;

    /**
     * 合并后的相对存储路径，未落盘时为空。
     */
    private String storagePath;

    /**
     * 已上传字节数。
     */
    private Long uploadedBytes;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

}
