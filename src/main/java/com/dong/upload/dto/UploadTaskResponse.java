package com.dong.upload.dto;

import lombok.Data;

/**
 * 上传任务列表项。
 *
 * <p>刻意不返回已收分片的明细：一个 20GB 的任务能切出四千个分片，
 * 列表里带上它们会把响应撑到几百 KB，而列表场景根本不需要。
 * 要看明细走 status 接口。
 */
@Data
public class UploadTaskResponse {

    /**
     * 上传任务号。
     */
    private String uploadId;

    /**
     * 文件名。
     */
    private String fileName;

    /**
     * 文件总大小。
     */
    private Long fileSize;

    /**
     * 分片大小。
     */
    private Integer chunkSize;

    /**
     * 总分片数。
     */
    private Integer totalChunks;

    /**
     * 已接收分片数。
     */
    private Integer uploadedChunks;

    /**
     * 文件指纹，抽样模式下只展示前 16 位避免列太长。
     */
    private String fileHash;

    /**
     * 指纹计算方式。
     */
    private String hashMode;

    /**
     * 状态编码。
     */
    private Integer status;

    /**
     * 状态名称。
     */
    private String statusName;

    /**
     * 创建时间。
     */
    private String createTime;

}
