package com.dong.rpc.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回声调用请求。
 */
@Data
public class RpcEchoRequest {

    /**
     * 文本。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String text = "hello rpc";

}
