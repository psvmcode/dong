package com.dong.agent.mapper;

import com.dong.agent.enums.MessageRole;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 消息角色枚举类型处理器。
 *
 * <p>存的是协议里的角色名（小写）而不是枚举字面量，
 * 回放时直接可读，也不用额外做一次映射。
 */
@MappedTypes(MessageRole.class)
public class MessageRoleTypeHandler extends BaseTypeHandler<MessageRole> {

    /**
     * 将角色名写入 PreparedStatement。
     *
     * @param ps        PreparedStatement
     * @param i         参数索引
     * @param parameter 枚举值
     * @param jdbcType  JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, MessageRole parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getRole());
    }

    /**
     * 从结果集中读取角色名并反解为枚举，NULL 则返回 null。
     *
     * @param rs         结果集
     * @param columnName 列名
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public MessageRole getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null || value.isBlank() ? null : MessageRole.of(value.trim());
    }

    /**
     * 从结果集中读取角色名并反解为枚举，NULL 则返回 null。
     *
     * @param rs          结果集
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public MessageRole getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null || value.isBlank() ? null : MessageRole.of(value.trim());
    }

    /**
     * 从结果集中读取角色名并反解为枚举，NULL 则返回 null。
     *
     * @param cs          CallableStatement
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public MessageRole getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null || value.isBlank() ? null : MessageRole.of(value.trim());
    }

}
