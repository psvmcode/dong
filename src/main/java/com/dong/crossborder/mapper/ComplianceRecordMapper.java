package com.dong.crossborder.mapper;

import com.dong.crossborder.entity.ComplianceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 合规检查记录数据访问。记录只插入与查询，不提供更新：
 * 检查留痕一旦允许修改就失去监管证据的效力。
 */
@Mapper

public interface ComplianceRecordMapper {

    /**
     * 按 RemittanceNo 查询记录。
     */
    List<ComplianceRecord> selectByRemittanceNo(String remittanceNo);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(ComplianceRecord record);

    /**
     * 按结果统计记录数。
     */
    long countByResult(@Param("result") int result);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
