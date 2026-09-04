package com.dong.lab.redpacket.mapper;

import com.dong.lab.redpacket.enums.RedPacketStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
/**
 * 红包状态枚举类型处理器。
 */
@MappedTypes(RedPacketStatus.class)

public class RedPacketStatusTypeHandler extends BaseTypeHandler<RedPacketStatus> {

    @Override
    /**
     * 将枚举按 code 写入 PreparedStatement。
     *
     * @param ps PreparedStatement
     * @param i 参数索引
     * @param parameter 枚举值
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    public void setNonNullParameter(PreparedStatement ps, int i, RedPacketStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    /**
     * 从结果集中读取 code 并反解为枚举，NULL 则返回 null。
     *
     * @param rs 结果集
     * @param columnName 列名
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    public RedPacketStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : RedPacketStatus.of(value);
    }

    @Override
    /**
     * 从结果集中读取 code 并反解为枚举，NULL 则返回 null。
     *
     * @param rs 结果集
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    public RedPacketStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : RedPacketStatus.of(value);
    }

    @Override
    /**
     * 从结果集中读取 code 并反解为枚举，NULL 则返回 null。
     *
     * @param rs 结果集
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    public RedPacketStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : RedPacketStatus.of(value);
    }

}
