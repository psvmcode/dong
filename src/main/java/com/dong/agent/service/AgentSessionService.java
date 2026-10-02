package com.dong.agent.service;

import com.dong.agent.dto.MessageListQuery;
import com.dong.agent.dto.MessageResponse;
import com.dong.agent.dto.SessionCreateRequest;
import com.dong.agent.dto.SessionListQuery;
import com.dong.agent.dto.SessionResponse;
import com.dong.common.result.PageResult;

/**
 * Agent 会话服务。会话只承载上下文，一次执行的过程在运行与消息里。
 */
public interface AgentSessionService {

    /**
     * 创建会话。
     *
     * @param request 创建请求
     * @return 会话号
     */
    String create(SessionCreateRequest request);

    /**
     * 分页查询会话。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<SessionResponse> list(SessionListQuery query);

    /**
     * 查询会话详情。
     *
     * @param sessionNo 会话号
     * @return 会话详情
     */
    SessionResponse detail(String sessionNo);

    /**
     * 分页查询会话下的消息，含工具消息。
     *
     * @param sessionNo 会话号
     * @param query     查询条件
     * @return 分页结果
     */
    PageResult<MessageResponse> messages(String sessionNo, MessageListQuery query);

    /**
     * 删除会话及其消息与工具调用记录。
     *
     * @param sessionNo 会话号
     */
    void remove(String sessionNo);

    /**
     * 确保会话存在，不存在则创建。发起运行时不传会话号就走这条路径。
     *
     * @param sessionNo 会话号，允许为空
     * @return 可用的会话号
     */
    String ensure(String sessionNo);

    /**
     * 查询会话内下一个可用序号。消息按会话内序号排序回放，
     * 删除会话时整体删除，因此序号始终连续。
     *
     * @param sessionNo 会话号
     * @return 下一个序号
     */
    int nextSeq(String sessionNo);

}
