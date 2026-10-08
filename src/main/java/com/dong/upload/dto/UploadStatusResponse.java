package com.dong.upload.dto;

import lombok.Data;

/**
 * 上传进度响应。
 *
 * <p>uploaded_bytes 由已完成分片的字节数累加得出，不依赖前端上报，
 * 这样刷新页面之后拿到的进度才是服务端真实的收片情况。
 */
@Data
public class UploadStatusResponse {

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
     * 总分片数。
     */
    private Integer totalChunks;

    /**
     * 已接收分片数。
     */
    private Integer uploadedChunks;

    /**
     * 已上传字节数。
     */
    private Long uploadedBytes;

    /**
     * 完成百分比，取整便于展示。
     */
    private Integer percent;

    /**
     * 状态编码。
     */
    private Integer status;

    /**
     * 状态名称。
     */
    private String statusName;

}
