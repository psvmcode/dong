package com.dong.agent.dto;

import com.dong.common.constant.Constants;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 跑对照实验的请求。
 *
 * <p>modes 留空时用该实验的默认模式；rounds 用于同参数多跑几轮看波动——
 * 一轮的数字说明不了问题，尤其涉及远程 Redis 的实验。
 */
@Data
public class LabRunRequest {

    /**
     * 要对比的模式，留空用默认模式。
     */
    private List<String> modes = new ArrayList<>();

    /**
     * 每个模式跑几轮，默认 1，最多 5。
     */
    private Integer rounds = 1;

    /**
     * 自定义实验输入，留空用该实验的默认 prompt。
     */
    @Size(max = 4096)
    private String prompt;

    /**
     * 是否放行有副作用的工具。实验里要跑真实实验工具时必须打开。
     */
    private boolean confirmSideEffect = false;

    /**
     * 获取轮次。
     *
     * @return 轮次，限制在 1 到 5 之间
     */
    public int roundCount() {
        if (rounds == null || rounds < 1) {
            return 1;
        }
        return Math.min(rounds, Math.min(5, Constants.MAX_BATCH_SIZE));
    }

}
