package com.dong.lab.tcc.mapper;

import com.dong.lab.tcc.enums.TccTransactionStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@MappedTypes(TccTransactionStatus.class)
public class TccTransactionStatusTypeHandler extends BaseTypeHandler<TccTransactionStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, TccTransactionStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setInt(i, parameter.getCode());
    }

    @Override
    public TccTransactionStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : TccTransactionStatus.of(value);
    }

    @Override
    public TccTransactionStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        int value = rs.getInt(columnIndex);
        return rs.wasNull() ? null : TccTransactionStatus.of(value);
    }

    @Override
    public TccTransactionStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        int value = cs.getInt(columnIndex);
        return cs.wasNull() ? null : TccTransactionStatus.of(value);
    }

    public static class BranchStatusHandler extends BaseTypeHandler<com.dong.lab.tcc.enums.TccBranchStatus> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i,
                                        com.dong.lab.tcc.enums.TccBranchStatus parameter, JdbcType jdbcType)
                throws SQLException {
            ps.setInt(i, parameter.getCode());
        }

        @Override
        public com.dong.lab.tcc.enums.TccBranchStatus getNullableResult(ResultSet rs, String columnName)
                throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccBranchStatus.of(value);
        }

        @Override
        public com.dong.lab.tcc.enums.TccBranchStatus getNullableResult(ResultSet rs, int columnIndex)
                throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccBranchStatus.of(value);
        }

        @Override
        public com.dong.lab.tcc.enums.TccBranchStatus getNullableResult(CallableStatement cs, int columnIndex)
                throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : com.dong.lab.tcc.enums.TccBranchStatus.of(value);
        }

    }

    public static class OrderStatusHandler extends BaseTypeHandler<com.dong.lab.tcc.enums.TccOrderStatus> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i,
                                        com.dong.lab.tcc.enums.TccOrderStatus parameter, JdbcType jdbcType)
                throws SQLException {
            ps.setInt(i, parameter.getCode());
        }

        @Override
        public com.dong.lab.tcc.enums.TccOrderStatus getNullableResult(ResultSet rs, String columnName)
                throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccOrderStatus.of(value);
        }

        @Override
        public com.dong.lab.tcc.enums.TccOrderStatus getNullableResult(ResultSet rs, int columnIndex)
                throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : com.dong.lab.tcc.enums.TccOrderStatus.of(value);
        }

        @Override
        public com.dong.lab.tcc.enums.TccOrderStatus getNullableResult(CallableStatement cs, int columnIndex)
                throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : com.dong.lab.tcc.enums.TccOrderStatus.of(value);
        }

    }

}
