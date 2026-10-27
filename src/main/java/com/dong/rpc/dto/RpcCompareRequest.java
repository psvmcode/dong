package com.dong.rpc.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 对照实验请求。
 *
 * <p>次数必须封顶：每次远程调用都是一次真实网络往返，
 * 传一个天文数字等于把这台机器自己和自己打崩。
 */
@Data
public class RpcCompareRequest {

    /**
     * 文本。
     */
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String text = "hello rpc";

    /**
     * 每种方式调用多少次。
     */
    @Min(1)
    @Max(Constants.MAX_QUERY_LIMIT)
    private int times = 20;

}
