package com.dong.agent.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建会话请求。标题可以留空，首轮结束后由运行逻辑补上。
 */
@Data
public class SessionCreateRequest {

    /**
     * 会话标题，留空则由首轮输入自动截取。
     */
    @Size(max = 128)
    private String title;

}
