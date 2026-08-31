package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.ComplianceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ComplianceRecordMapper {

    List<ComplianceRecord> selectByRemittanceNo(String remittanceNo);

    int insert(ComplianceRecord record);

    long countByResult(@Param("result") int result);

    int clearAll();

}
