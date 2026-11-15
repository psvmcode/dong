package com.dong.ad.service;

import com.dong.ad.dto.BurstLabRequest;
import com.dong.ad.dto.BurstLabResponse;

/**
 * 广告计费并发对照实验。
 */
public interface AdLabService {

    /**
     * 用同一批预算并发打计费链路，对比 Redis 预扣与纯数据库条件扣减的耗时，
     * 并回读权威流水验证是否超投。
     *
     * @param request 实验参数
     * @return 实验结果
     */
    BurstLabResponse burst(BurstLabRequest request);

}
