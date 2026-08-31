package com.dong.lab.crossborder.dto;

import com.dong.lab.crossborder.enums.SettlementChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发起汇款请求。idempotentKey 由调用方生成并保证同一笔业务只用一个值，
 * 这样网络超时重试时不会重复汇款。
 */
@Data
public class RemittanceCreateRequest {

    @NotBlank
    private String idempotentKey;

    @NotBlank
    private String payerAccountNo;

    @NotBlank
    private String payeeAccountNo;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal sourceAmount;

    @NotNull
    private SettlementChannel channel;

    private String quoteNo;

}
