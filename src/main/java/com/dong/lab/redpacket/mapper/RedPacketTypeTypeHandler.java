package com.dong.lab.redpacket.mapper;

import com.dong.lab.redpacket.enums.RedPacketType;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(RedPacketType.class)
public class RedPacketTypeTypeHandler extends BaseTypeHandler<RedPacketType> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, RedPacketType parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public RedPacketType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : RedPacketType.of(value);
    }

    @Override
    public RedPacketType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : RedPacketType.of(value);
    }

    @Override
    public RedPacketType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : RedPacketType.of(value);
    }

}
