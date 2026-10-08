package com.dong.upload.controller;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageResult;
import com.dong.common.result.Result;
import com.dong.upload.dto.UploadInitRequest;
import com.dong.upload.dto.UploadInitResponse;
import com.dong.upload.dto.UploadIdRequest;
import com.dong.upload.dto.UploadStatusResponse;
import com.dong.upload.dto.UploadTaskQuery;
import com.dong.upload.dto.UploadTaskResponse;
import com.dong.upload.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件分片上传接口。
 *
 * <p>唯一一个不走 JSON body 的接口是 chunk——它用 multipart/form-data，
 * 因为要传二进制分片。分片本身是字节流，塞进 JSON 会先 base64 膨胀 33%，
 * 对 GB 级文件等于凭空多传几百 MB。
 */
@RestController
@Validated
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Tag(name = "文件分片上传")
public class UploadController {

    /**
     * uploadService，业务服务层。
     */
    private final UploadService uploadService;

    /**
     * 模块开关，关闭时所有接口返回 1004 而不是底层异常。
     */
    @Value("${dong.upload.enabled:true}")
    private boolean enabled;

    @PostMapping("/init")
    @Operation(summary = "初始化上传，返回是否秒传与已收分片")
    public Result<UploadInitResponse> init(@Valid @RequestBody UploadInitRequest request) {
        checkAvailable();
        return Result.success(uploadService.init(request));
    }

    @PostMapping("/chunk")
    @Operation(summary = "上传单个分片，同一分片重复上传是幂等的")
    public Result<Long> chunk(@RequestParam @NotBlank @Size(max = 64) String uploadId,
                              @RequestParam @Min(0) Integer chunkIndex,
                              @RequestParam(required = false) @Size(max = 64) String chunkHash,
                              @RequestParam("file") MultipartFile file) {
        checkAvailable();
        return Result.success(uploadService.receiveChunk(uploadId, chunkIndex, chunkHash, file));
    }

    @PostMapping("/complete")
    @Operation(summary = "合并所有分片并标记完成")
    public Result<String> complete(@Valid @RequestBody UploadIdRequest request) {
        checkAvailable();
        return Result.success(uploadService.complete(request.getUploadId()));
    }

    @PostMapping("/status")
    @Operation(summary = "查询上传进度，断点续传后用它看还差多少")
    public Result<UploadStatusResponse> status(@Valid @RequestBody UploadIdRequest request) {
        checkAvailable();
        return Result.success(uploadService.status(request.getUploadId()));
    }

    @PostMapping("/tasks")
    @Operation(summary = "分页查询上传任务")
    public Result<PageResult<UploadTaskResponse>> tasks(@Valid @RequestBody UploadTaskQuery query) {
        checkAvailable();
        return Result.success(uploadService.page(query));
    }

    @DeleteMapping("/{uploadId}")
    @Operation(summary = "取消上传任务，清理分片记录与文件")
    public Result<Void> cancel(@PathVariable @NotBlank @Size(max = 64) String uploadId) {
        checkAvailable();
        uploadService.cancel(uploadId);
        return Result.success();
    }

    /**
     * 模块开关未开时统一抛 1004，避免把底层异常暴露成 5000。
     */
    private void checkAvailable() {
        if (!enabled) {
            throw new BusinessException(Constants.CODE_MIDDLEWARE_DISABLED, "upload module is disabled");
        }
    }

}
