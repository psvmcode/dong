package com.dong.logging.dto;

import lombok.Data;

import java.util.List;

/**
 * 上下文查询结果。三段分开返回而不是拼成一个列表：
 * 前端要把目标那条高亮出来，拼一起就得让前端再去比对 id。
 */
@Data
public class LogContextResponse {

    /**
     * 目标日志之前的日志，按时间正序，越靠后离目标越近。
     */
    private List<LogSearchResponse.Hit> before;

    /**
     * 目标日志。
     */
    private LogSearchResponse.Hit target;

    /**
     * 目标日志之后的日志，按时间正序，越靠前离目标越近。
     */
    private List<LogSearchResponse.Hit> after;

}
