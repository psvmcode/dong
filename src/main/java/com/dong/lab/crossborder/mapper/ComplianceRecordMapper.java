package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.ComplianceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 合规检查记录数据访问。记录只插入与查询，不提供更新：
 * 检查留痕一旦允许修改就失去监管证据的效力。
 */
@Mapper
public interface ComplianceRecordMapper {

    List<ComplianceRecord> selectByRemittanceNo(String remittanceNo);

    int insert(ComplianceRecord record);

    long countByResult(@Param("result") int result);

    int clearAll();

}
