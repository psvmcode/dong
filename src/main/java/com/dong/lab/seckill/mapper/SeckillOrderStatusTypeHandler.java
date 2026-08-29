package com.dong.lab.seckill.mapper;

import com.dong.lab.seckill.enums.SeckillOrderStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(SeckillOrderStatus.class)
public class SeckillOrderStatusTypeHandler extends BaseTypeHandler<SeckillOrderStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, SeckillOrderStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public SeckillOrderStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : SeckillOrderStatus.of(value);
    }

    @Override
    public SeckillOrderStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : SeckillOrderStatus.of(value);
    }

    @Override
    public SeckillOrderStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : SeckillOrderStatus.of(value);
    }

}
