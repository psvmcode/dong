package com.dong.crossborder.mapper;

import com.dong.crossborder.entity.FxRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
/**
 * 汇率牌价数据访问。
 */
@Mapper

public interface FxRateMapper {

    /**
     * 按币种查牌价，只返回启用状态。
     */
    FxRate selectByCurrency(@Param("currency") String currency);

    /**
     * 查询全部牌价，管理接口使用。
     */
    List<FxRate> selectAll();

    /**
     * 新增牌价。
     */
    int insert(FxRate rate);

    /**
     * 更新牌价，调整汇率时调用。
     */
    int updateRate(@Param("currency") String currency, @Param("usdRate") BigDecimal usdRate);

}
