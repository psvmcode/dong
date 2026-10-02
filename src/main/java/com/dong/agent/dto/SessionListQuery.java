package com.dong.agent.dto;

import com.dong.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话分页查询请求。分页参数与上限都在 PageQuery 里，这里不加多余条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SessionListQuery extends PageQuery {
}
