package com.dong.lab.seckill.mapper;

import com.dong.lab.seckill.enums.SeckillActivityStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(SeckillActivityStatus.class)
public class SeckillActivityStatusTypeHandler extends BaseTypeHandler<SeckillActivityStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, SeckillActivityStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public SeckillActivityStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : SeckillActivityStatus.of(value);
    }

    @Override
    public SeckillActivityStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : SeckillActivityStatus.of(value);
    }

    @Override
    public SeckillActivityStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : SeckillActivityStatus.of(value);
    }

}
