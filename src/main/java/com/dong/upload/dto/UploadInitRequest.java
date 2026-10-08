package com.dong.upload.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上传初始化请求。
 *
 * <p>前端在计算完文件指纹之后调用，带上指纹、大小与分片策略。
 * 后端据此判断三种情况：能秒传、能续传、还是得从头开始。
 *
 * <p>file_size 必须给，它不只是展示用——抽样指纹必须联合文件大小才能判定同一性，
 * 少了它，两个不同文件只要抽样块碰巧一样就会被误判成秒传。
 */
@Data
public class UploadInitRequest {

    /**
     * 原始文件名。
     */
    @NotBlank
    @Size(max = 255)
    private String fileName;

    /**
     * 文件总大小，单位字节。
     */
    @Min(1)
    @Max(Constants.MAX_FILE_SIZE)
    private Long fileSize;

    /**
     * 分片大小，不传用后端默认配置。
     */
    @Min(1024)
    @Max(Constants.MAX_CHUNK_SIZE)
    private Integer chunkSize;

    /**
     * 文件指纹。
     */
    @NotBlank
    @Size(max = 64)
    private String fileHash;

    /**
     * 指纹计算方式：full 全量、sample 抽样。
     */
    @NotBlank
    @Pattern(regexp = "full|sample")
    private String hashMode;

}
