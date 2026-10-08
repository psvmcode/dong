package com.dong.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 按上传任务号操作的通用请求。
 *
 * <p>查询进度、合并、取消都只依赖 upload_id，共用一个 DTO 省掉三份几乎一样的类。
 */
@Data
public class UploadIdRequest {

    /**
     * 上传任务号。
     */
    @NotBlank
    @Size(max = 64)
    private String uploadId;

}
