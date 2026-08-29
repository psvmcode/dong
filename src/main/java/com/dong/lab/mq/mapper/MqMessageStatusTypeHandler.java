package com.dong.lab.mq.mapper;

import com.dong.lab.mq.enums.MqMessageStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(MqMessageStatus.class)
public class MqMessageStatusTypeHandler extends BaseTypeHandler<MqMessageStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, MqMessageStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public MqMessageStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : MqMessageStatus.of(value);
    }

    @Override
    public MqMessageStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : MqMessageStatus.of(value);
    }

    @Override
    public MqMessageStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : MqMessageStatus.of(value);
    }

}
