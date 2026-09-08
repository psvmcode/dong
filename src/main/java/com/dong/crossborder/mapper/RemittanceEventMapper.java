package com.dong.crossborder.mapper;

import com.dong.crossborder.entity.RemittanceEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 汇款单流转日志数据访问。
 */
@Mapper

public interface RemittanceEventMapper {

    /**
     * 追加一条流转记录。日志只增不改，修改历史等于篡改证据。
     */
    int insert(RemittanceEvent event);

    /**
     * 按汇款单号查完整流转历史，按发生时间升序。
     */
    List<RemittanceEvent> selectByRemittanceNo(@Param("remittanceNo") String remittanceNo);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
