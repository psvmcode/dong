package com.dong.upload.dto;

import lombok.Data;

/**
 * 某个任务已收分片数的投影。
 *
 * <p>专门为列表查询准备：分页查任务时如果逐条 count 一次，一页 100 条就是 100 次往返，
 * 用这个投影一次 group by 就能拿全，再在内存里按 upload_id 拼回去。
 */
@Data
public class UploadChunkCount {

    /**
     * 上传任务号。
     */
    private String uploadId;

    /**
     * 已接收分片数。
     */
    private long count;

}
