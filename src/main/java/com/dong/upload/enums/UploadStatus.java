package com.dong.upload.enums;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 上传任务状态。
 *
 * <p>只有三种：上传中、已完成、已取消。
 * 没有「合并中」这种中间态——合并要么成功推进到已完成，要么失败保持上传中让前端重试，
 * 引入中间态就得再写一套补偿，而这个场景里重试合并是幂等的，不需要。
 */
@Getter
@AllArgsConstructor
public enum UploadStatus {

    /**
     * 上传中，分片还没收齐。
     */
    UPLOADING(0),

    /**
     * 已完成，分片收齐且合并成功。
     */
    COMPLETED(1),

    /**
     * 已取消，任务作废，等待清理。
     */
    CANCELLED(2);

    /**
     * 状态编码，落库存储。
     */
    private final int code;

    /**
     * 根据编码获取上传状态枚举。
     *
     * @param code 状态编码
     * @return 上传状态枚举
     */
    public static UploadStatus of(int code) {
        for (UploadStatus status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new BusinessException(Constants.CODE_PARAM_INVALID, "unknown upload status " + code);
    }

    /**
     * 判断是否已经是终态，终态不再接受分片。
     *
     * @return true 表示任务已结束
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

}
