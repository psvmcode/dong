package com.dong.upload.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 上传初始化响应。
 *
 * <p>instant 为 true 时表示命中秒传，前端不需要再传任何分片。
 * 否则 uploadedChunks 就是「已经收过的分片下标」，前端应当跳过它们，
 * 只补传剩下的——这就是断点续传能续上的全部依据。
 */
@Data
public class UploadInitResponse {

    /**
     * 上传任务号。
     */
    private String uploadId;

    /**
     * 是否命中秒传，命中则无需上传。
     */
    private boolean instant;

    /**
     * 总分片数。
     */
    private int totalChunks;

    /**
     * 已接收的分片下标，断点续传时前端跳过这些。
     */
    private List<Integer> uploadedChunks = new ArrayList<>();

    /**
     * 当前任务状态编码。
     */
    private int status;

}
