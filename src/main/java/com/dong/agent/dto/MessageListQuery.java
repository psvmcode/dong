package com.dong.agent.dto;

import com.dong.common.result.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息分页查询请求。会话号在路径上，这里只带分页参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageListQuery extends PageQuery {
}
