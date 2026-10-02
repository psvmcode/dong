package com.dong.agent.service.impl;

import com.dong.agent.dto.MessageListQuery;
import com.dong.agent.dto.MessageResponse;
import com.dong.agent.dto.SessionCreateRequest;
import com.dong.agent.dto.SessionListQuery;
import com.dong.agent.dto.SessionResponse;
import com.dong.agent.entity.AgentMessage;
import com.dong.agent.entity.AgentSession;
import com.dong.agent.enums.SessionStatus;
import com.dong.agent.mapper.AgentMessageMapper;
import com.dong.agent.mapper.AgentSessionMapper;
import com.dong.agent.mapper.AgentToolCallMapper;
import com.dong.agent.service.AgentSessionService;
import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageRequest;
import com.dong.common.result.PageResult;
import com.dong.common.util.Snowflake;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话服务实现。会话只承载上下文，因此这里的操作都很轻，
 * 真正重的是运行服务里的循环与落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentSessionServiceImpl implements AgentSessionService {

    /**
     * 会话号前缀。
     */
    private static final String SESSION_PREFIX = "AS";

    /**
     * sessionMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentSessionMapper sessionMapper;

    /**
     * messageMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentMessageMapper messageMapper;

    /**
     * toolCallMapper，MyBatis Mapper 数据访问层。
     */
    private final AgentToolCallMapper toolCallMapper;

    /**
     * snowflake，雪花发号器。
     */
    private final Snowflake snowflake;

    /**
     * 创建会话。
     *
     * @param request 创建请求
     * @return 会话号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(SessionCreateRequest request) {
        AgentSession session = new AgentSession();
        session.setSessionNo(SESSION_PREFIX + snowflake.nextIdStr());
        session.setTitle(request.getTitle() == null ? "" : request.getTitle().trim());
        session.setModel("");
        session.setStatus(SessionStatus.ACTIVE);
        session.setMessageCount(0);
        session.setRunCount(0);
        session.setSummary("");
        sessionMapper.insert(session);
        return session.getSessionNo();
    }

    /**
     * 分页查询会话。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<SessionResponse> list(SessionListQuery query) {
        PageRequest page = query.toPageRequest();
        List<AgentSession> sessions = sessionMapper.selectByPage(page.getOffset(), page.getPageSize());
        List<SessionResponse> responses = sessions.stream().map(this::toResponse).toList();
        return PageResult.of(responses, sessionMapper.countAll(), page);
    }

    /**
     * 查询会话详情。
     *
     * @param sessionNo 会话号
     * @return 会话详情
     */
    @Override
    public SessionResponse detail(String sessionNo) {
        return toResponse(require(sessionNo));
    }

    /**
     * 分页查询会话下的消息，含工具消息。
     *
     * @param sessionNo 会话号
     * @param query     查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<MessageResponse> messages(String sessionNo, MessageListQuery query) {
        require(sessionNo);
        PageRequest page = query.toPageRequest();
        List<AgentMessage> messages = messageMapper.selectBySession(sessionNo, page.getOffset(), page.getPageSize());
        List<MessageResponse> responses = messages.stream().map(this::toMessageResponse).toList();
        return PageResult.of(responses, messageMapper.countBySession(sessionNo), page);
    }

    /**
     * 删除会话及其消息与工具调用记录。
     *
     * @param sessionNo 会话号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(String sessionNo) {
        AgentSession session = require(sessionNo);
        toolCallMapper.deleteBySessionNo(sessionNo);
        messageMapper.deleteBySessionNo(sessionNo);
        sessionMapper.deleteBySessionNo(sessionNo);
        log.info("agent session removed sessionNo={} messages={}", sessionNo, session.getMessageCount());
    }

    /**
     * 确保会话存在，不存在则创建。
     *
     * @param sessionNo 会话号，允许为空
     * @return 可用的会话号
     */
    @Override
    public String ensure(String sessionNo) {
        if (sessionNo == null || sessionNo.isBlank()) {
            return create(new SessionCreateRequest());
        }
        return require(sessionNo).getSessionNo();
    }

    /**
     * 查询会话内下一个可用序号。
     *
     * @param sessionNo 会话号
     * @return 下一个序号
     */
    @Override
    public int nextSeq(String sessionNo) {
        Integer maxSeq = messageMapper.selectMaxSeq(sessionNo);
        return (maxSeq == null ? 0 : maxSeq) + 1;
    }

    /**
     * 按会话号查询，不存在则报数据不存在。
     *
     * @param sessionNo 会话号
     * @return 会话实体
     */
    private AgentSession require(String sessionNo) {
        AgentSession session = sessionMapper.selectBySessionNo(sessionNo);
        if (session == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "agent session not found " + sessionNo);
        }
        return session;
    }

    /**
     * 转换为响应对象。
     *
     * @param session 会话实体
     * @return 会话响应
     */
    private SessionResponse toResponse(AgentSession session) {
        SessionResponse response = new SessionResponse();
        response.setSessionNo(session.getSessionNo());
        response.setTitle(session.getTitle());
        response.setModel(session.getModel());
        response.setStatus(session.getStatus() == null ? null : session.getStatus().getCode());
        response.setMessageCount(session.getMessageCount());
        response.setRunCount(session.getRunCount());
        response.setSummary(session.getSummary());
        response.setCreateTime(session.getCreateTime());
        response.setUpdateTime(session.getUpdateTime());
        return response;
    }

    /**
     * 转换为消息响应。
     *
     * @param message 消息实体
     * @return 消息响应
     */
    private MessageResponse toMessageResponse(AgentMessage message) {
        MessageResponse response = new MessageResponse();
        response.setSeq(message.getSeq());
        response.setRole(message.getRole() == null ? "" : message.getRole().getRole());
        response.setContent(message.getContent());
        response.setToolName(message.getToolName());
        response.setTruncated(message.getTruncated() != null && message.getTruncated() == 1);
        response.setCreateTime(message.getCreateTime());
        return response;
    }

}
