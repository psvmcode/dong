package com.dong.agent.mapper;

import com.dong.agent.enums.FinishReason;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 结束原因枚举类型处理器。
 *
 * <p>空串必须映射成 null 而不是抛异常：运行记录刚创建时还没有结束原因，
 * 库里就是默认的空串，此时查询这条记录是正常操作，不该被反解失败打断。
 */
@MappedTypes(FinishReason.class)
public class FinishReasonTypeHandler extends BaseTypeHandler<FinishReason> {

    /**
     * 将枚举名写入 PreparedStatement。
     *
     * @param ps        PreparedStatement
     * @param i         参数索引
     * @param parameter 枚举值
     * @param jdbcType  JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, FinishReason parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    /**
     * 从结果集中读取枚举名并反解，空值与空串都返回 null。
     *
     * @param rs         结果集
     * @param columnName 列名
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public FinishReason getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null || value.isBlank() ? null : FinishReason.valueOf(value.trim());
    }

    /**
     * 从结果集中读取枚举名并反解，空值与空串都返回 null。
     *
     * @param rs          结果集
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public FinishReason getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null || value.isBlank() ? null : FinishReason.valueOf(value.trim());
    }

    /**
     * 从结果集中读取枚举名并反解，空值与空串都返回 null。
     *
     * @param cs          CallableStatement
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public FinishReason getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null || value.isBlank() ? null : FinishReason.valueOf(value.trim());
    }

}
