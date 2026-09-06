package com.dong.crossborder.mapper;

import com.dong.crossborder.entity.CrossBorderAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
/**
 * CrossBorderAccountMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface CrossBorderAccountMapper {

    /**
     * 按 AccountNo 查询记录。
     */
    CrossBorderAccount selectByAccountNo(String accountNo);

    /**
     * 根据 id 查询记录。
     */
    CrossBorderAccount selectById(Long id);

    /**
     * 查询所有记录。
     */
    List<CrossBorderAccount> selectAll();

    /**
     * 按 id 批量查询。列表场景一次性取回，避免逐条查询造成的 N+1。
     */
    List<CrossBorderAccount> selectByIds(@Param("ids") java.util.Collection<Long> ids);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(CrossBorderAccount account);

    /**
     * 扣减余额。带上余额条件保证不会扣成负数，这是资金安全的基本保障。
     */
    int deduct(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("version") int version);

    /**
     * 增加余额。
     */
    int credit(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 冻结余额。
     */
    int freeze(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 解冻余额。
     */
    int unfreeze(@Param("id") Long id, @Param("amount") BigDecimal amount);

    /**
     * 账户级状态变更（冻结/解冻）。带上期望状态做条件更新，
     * 并发冻结或重复解冻时只有一个请求生效，其余返回 0 由上层拒绝。
     */
    int updateStatus(@Param("id") Long id, @Param("status") int status, @Param("expectedStatus") int expectedStatus);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
