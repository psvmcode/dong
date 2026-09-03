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
 *
 * <p>channel 可以不传，交给渠道路由按成本与时效自动选择；
 * 传了则尊重调用方的指定，适合有渠道偏好的场景。
 */
@Data
public class RemittanceCreateRequest {

    /**
     * 幂等键，由调用方生成并保证同一笔业务只使用一次，用于防止网络超时重试导致重复汇款。
     */
    @NotBlank
    private String idempotentKey;

    /**
     * 付款账户编号，资金将从该账户扣减。
     */
    @NotBlank
    private String payerAccountNo;

    /**
     * 收款账户编号，资金将汇入该账户。
     */
    @NotBlank
    private String payeeAccountNo;

    /**
     * 源币种金额，即希望汇出的原始金额。
     */
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal sourceAmount;

    /**
     * 清算渠道，不指定时由服务端按成本和时效自动路由。
     */
    private SettlementChannel channel;

    /**
     * 是否加急，加急单可能进入更快但费用更高的渠道。
     */
    private Boolean urgent;

    /**
     * 锁价报价编号，传入后按该报价汇率成交。
     */
    private String quoteNo;

}
