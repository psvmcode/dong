package com.dong.upload.dto;

import com.dong.common.result.PageQuery;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 上传任务分页查询条件。
 *
 * <p>继承 PageQuery 复用 pageNum 与 pageSize 的校验，不重复写一遍默认值与上限。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UploadTaskQuery extends PageQuery {

    /**
     * 按状态过滤，不传则查全部。越界值由 UploadStatus.of 兜成 1000，不在这里硬编码上限。
     */
    @Min(0)
    private Integer status;

}
