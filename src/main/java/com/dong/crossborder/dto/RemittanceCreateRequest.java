package com.dong.crossborder.dto;

import com.dong.crossborder.enums.SettlementChannel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
/**
 * 发起汇款请求。idempotentKey 由调用方生成并保证同一笔业务只用一个值，
 * 这样网络超时重试时不会重复汇款。
 *
 * <p>channel 可以不传，交给渠道路由按成本与时效自动选择；
 * 传了则尊重调用方的指定，适合有渠道偏好的场景。
 *
 * <p>所有字符串都按数据库字段长度限制：超长输入会撞 Data too long 变成 500，
 * 那是基础设施错误而不是业务拒绝，掩盖了真正的原因。
 * 金额限制两位小数是因为 source_amount 落库为 decimal(18,2)，
 * 传更多小数会被静默四舍五入，导致扣款金额与单子记录对不上。
 */
@Data

public class RemittanceCreateRequest {

    /**
     * 幂等键，由调用方生成并保证同一笔业务只使用一次，用于防止网络超时重试导致重复汇款。
     */
    @NotBlank
    @Size(max = 64)
    private String idempotentKey;

    /**
     * 付款账户编号，资金将从该账户扣减。
     */
    @NotBlank
    @Size(max = 32)
    private String payerAccountNo;

    /**
     * 收款账户编号，资金将汇入该账户。
     */
    @NotBlank
    @Size(max = 32)
    private String payeeAccountNo;

    /**
     * 源币种金额，即希望汇出的原始金额。
     */
    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 16, fraction = 2)
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
    @Size(max = 32)
    private String quoteNo;

}
