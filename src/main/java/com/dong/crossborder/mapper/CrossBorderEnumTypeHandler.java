package com.dong.crossborder.mapper;

import com.dong.crossborder.enums.AccountEventType;
import com.dong.crossborder.enums.ComplianceCheckType;
import com.dong.crossborder.enums.ComplianceResult;
import com.dong.crossborder.enums.FxQuoteStatus;
import com.dong.crossborder.enums.LedgerDirection;
import com.dong.crossborder.enums.ReconDiffType;
import com.dong.crossborder.enums.RemittanceStatus;
import com.dong.crossborder.enums.SettlementChannel;
import com.dong.crossborder.enums.SettlementStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 跨境支付枚举的 TypeHandler 集合。枚举落库统一用 int，
 * 读取时按 code 反解，未知的 code 直接抛错而不是静默映射，
 * 避免脏数据被当成合法状态继续流转。
 */
public final class CrossBorderEnumTypeHandler {

    /**
     * CrossBorderEnumTypeHandler。
     */
    private CrossBorderEnumTypeHandler() {
    }

    @MappedTypes(RemittanceStatus.class)
    public static class RemittanceStatusHandler extends BaseTypeHandler<RemittanceStatus> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, RemittanceStatus parameter, JdbcType jdbcType)
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
        public RemittanceStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : RemittanceStatus.of(value);
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
        public RemittanceStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : RemittanceStatus.of(value);
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
        public RemittanceStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : RemittanceStatus.of(value);
        }

    }

    @MappedTypes(FxQuoteStatus.class)
    public static class FxQuoteStatusHandler extends BaseTypeHandler<FxQuoteStatus> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, FxQuoteStatus parameter, JdbcType jdbcType)
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
        public FxQuoteStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : FxQuoteStatus.of(value);
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
        public FxQuoteStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : FxQuoteStatus.of(value);
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
        public FxQuoteStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : FxQuoteStatus.of(value);
        }

    }

    @MappedTypes(ComplianceCheckType.class)
    public static class ComplianceCheckTypeHandler extends BaseTypeHandler<ComplianceCheckType> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, ComplianceCheckType parameter, JdbcType jdbcType)
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
        public ComplianceCheckType getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : ComplianceCheckType.of(value);
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
        public ComplianceCheckType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : ComplianceCheckType.of(value);
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
        public ComplianceCheckType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : ComplianceCheckType.of(value);
        }

    }

    @MappedTypes(ComplianceResult.class)
    public static class ComplianceResultHandler extends BaseTypeHandler<ComplianceResult> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, ComplianceResult parameter, JdbcType jdbcType)
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
        public ComplianceResult getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : ComplianceResult.of(value);
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
        public ComplianceResult getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : ComplianceResult.of(value);
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
        public ComplianceResult getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : ComplianceResult.of(value);
        }

    }

    @MappedTypes(LedgerDirection.class)
    public static class LedgerDirectionHandler extends BaseTypeHandler<LedgerDirection> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, LedgerDirection parameter, JdbcType jdbcType)
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
        public LedgerDirection getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : LedgerDirection.of(value);
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
        public LedgerDirection getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : LedgerDirection.of(value);
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
        public LedgerDirection getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : LedgerDirection.of(value);
        }

    }

    @MappedTypes(SettlementChannel.class)
    public static class SettlementChannelHandler extends BaseTypeHandler<SettlementChannel> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, SettlementChannel parameter, JdbcType jdbcType)
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
        public SettlementChannel getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : SettlementChannel.of(value);
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
        public SettlementChannel getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : SettlementChannel.of(value);
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
        public SettlementChannel getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : SettlementChannel.of(value);
        }

    }

    @MappedTypes(SettlementStatus.class)
    public static class SettlementStatusHandler extends BaseTypeHandler<SettlementStatus> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, SettlementStatus parameter, JdbcType jdbcType)
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
        public SettlementStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : SettlementStatus.of(value);
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
        public SettlementStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : SettlementStatus.of(value);
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
        public SettlementStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : SettlementStatus.of(value);
        }

    }

    @MappedTypes(ReconDiffType.class)
    public static class ReconDiffTypeHandler extends BaseTypeHandler<ReconDiffType> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, ReconDiffType parameter, JdbcType jdbcType)
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
        public ReconDiffType getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : ReconDiffType.of(value);
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
        public ReconDiffType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : ReconDiffType.of(value);
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
        public ReconDiffType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : ReconDiffType.of(value);
        }

    }

    /**
     * 账户事件类型（冻结/解冻）的落库转换。事件类型错映射会让
     * 冻结记录被读成解冻，审计口径直接失真，因此未知 code 同样直接抛错。
     */
    @MappedTypes(AccountEventType.class)
    public static class AccountEventTypeHandler extends BaseTypeHandler<AccountEventType> {

        @Override
        public void setNonNullParameter(PreparedStatement ps, int i, AccountEventType parameter, JdbcType jdbcType)
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
        public AccountEventType getNullableResult(ResultSet rs, String columnName) throws SQLException {
            int value = rs.getInt(columnName);
            return rs.wasNull() ? null : AccountEventType.of(value);
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
        public AccountEventType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
            int value = rs.getInt(columnIndex);
            return rs.wasNull() ? null : AccountEventType.of(value);
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
        public AccountEventType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
            int value = cs.getInt(columnIndex);
            return cs.wasNull() ? null : AccountEventType.of(value);
        }

    }

}
