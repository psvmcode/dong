package com.dong.lab.seckill.mapper;

import com.dong.lab.seckill.enums.SeckillActivityStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
/**
 * 秒杀活动状态枚举类型处理器。
 */
@MappedTypes(SeckillActivityStatus.class)

public class SeckillActivityStatusTypeHandler extends BaseTypeHandler<SeckillActivityStatus> {

    /**
     * 将枚举按 code 写入 PreparedStatement。
     *
     * @param ps PreparedStatement
     * @param i 参数索引
     * @param parameter 枚举值
     * @param jdbcType JDBC 类型
     * @throws SQLException SQL 异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, SeckillActivityStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    /**
     * 从结果集中读取 code 并反解为枚举，NULL 则返回 null。
     *
     * @param rs 结果集
     * @param columnName 列名
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public SeckillActivityStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : SeckillActivityStatus.of(value);
    }

    /**
     * 从结果集中读取 code 并反解为枚举，NULL 则返回 null。
     *
     * @param rs 结果集
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public SeckillActivityStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : SeckillActivityStatus.of(value);
    }

    /**
     * 从结果集中读取 code 并反解为枚举，NULL 则返回 null。
     *
     * @param rs 结果集
     * @param columnIndex 列索引
     * @return 枚举值或 null
     * @throws SQLException SQL 异常
     */
    @Override
    public SeckillActivityStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : SeckillActivityStatus.of(value);
    }

}
