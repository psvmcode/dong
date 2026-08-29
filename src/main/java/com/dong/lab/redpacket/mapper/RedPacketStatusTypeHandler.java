package com.dong.lab.redpacket.mapper;

import com.dong.lab.redpacket.enums.RedPacketStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(RedPacketStatus.class)
public class RedPacketStatusTypeHandler extends BaseTypeHandler<RedPacketStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, RedPacketStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public RedPacketStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : RedPacketStatus.of(value);
    }

    @Override
    public RedPacketStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : RedPacketStatus.of(value);
    }

    @Override
    public RedPacketStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : RedPacketStatus.of(value);
    }

}
