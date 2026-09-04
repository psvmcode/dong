package com.dong.lab.tcc.mapper;

import com.dong.lab.tcc.enums.TccTransactionStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
/**
 * TCC 事务状态枚举类型处理器。
 */
@MappedTypes(TccTransactionStatus.class)

public class TccTransactionStatusTypeHandler extends BaseTypeHandler<TccTransactionStatus> {

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
    public void setNonNullParameter(PreparedStatement ps, int i, TccTransactionStatus parameter, JdbcType jdbcType)
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
    public TccTransactionStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : TccTransactionStatus.of(value);
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
    public TccTransactionStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : TccTransactionStatus.of(value);
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
    public TccTransactionStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : TccTransactionStatus.of(value);
    }

    public static class BranchStatusHandler extends BaseTypeHandler<com.dong.lab.tcc.enums.TccBranchStatus> {

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
        public void setNonNullParameter(PreparedStatement ps, int i,
                                        com.dong.lab.tcc.enums.TccBranchStatus parameter, JdbcType jdbcType)
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
        public com.dong.lab.tcc.enums.TccBranchStatus getNullableResult(ResultSet rs, String columnName)
                throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccBranchStatus.of(value);
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
        public com.dong.lab.tcc.enums.TccBranchStatus getNullableResult(ResultSet rs, int columnIndex)
                throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccBranchStatus.of(value);
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
        public com.dong.lab.tcc.enums.TccBranchStatus getNullableResult(CallableStatement cs, int columnIndex)
                throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : com.dong.lab.tcc.enums.TccBranchStatus.of(value);
        }

    }

    public static class OrderStatusHandler extends BaseTypeHandler<com.dong.lab.tcc.enums.TccOrderStatus> {

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
        public void setNonNullParameter(PreparedStatement ps, int i,
                                        com.dong.lab.tcc.enums.TccOrderStatus parameter, JdbcType jdbcType)
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
        public com.dong.lab.tcc.enums.TccOrderStatus getNullableResult(ResultSet rs, String columnName)
                throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccOrderStatus.of(value);
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
        public com.dong.lab.tcc.enums.TccOrderStatus getNullableResult(ResultSet rs, int columnIndex)
                throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccOrderStatus.of(value);
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
        public com.dong.lab.tcc.enums.TccOrderStatus getNullableResult(CallableStatement cs, int columnIndex)
                throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : com.dong.lab.tcc.enums.TccOrderStatus.of(value);
        }

    }

}
