package com.dong.ad.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 开立广告主账户请求。
 */
@Data
public class AdvertiserCreateRequest {

    /**
     * 广告主名称。
     */
    @NotBlank
    @Size(max = Constants.MAX_NAME_LENGTH)
    private String name;

    /**
     * 初始充值金额，单位分。开户不带钱会得到一个立刻停投的账户。
     */
    @NotNull
    @Min(1)
    @Max(Constants.MAX_MONEY_CENTS)
    private Long balanceCents;

}
