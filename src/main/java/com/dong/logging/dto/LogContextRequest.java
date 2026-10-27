package com.dong.logging.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 上下文查询请求。对应 Kibana 里「查看某条日志周围发生了什么」——
 * 一条 ERROR 本身常常说明不了问题，真正的原因在它前面几行。
 */
@Data
public class LogContextRequest {

    /**
     * 目标日志的文档 id。
     */
    @NotBlank
    @Size(max = 64)
    private String id;

    /**
     * 往前取多少条。
     */
    @Min(0)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int before = 10;

    /**
     * 往后取多少条。
     */
    @Min(0)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int after = 10;

}
