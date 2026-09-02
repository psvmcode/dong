package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.CrossBorderAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface CrossBorderAccountMapper {

    CrossBorderAccount selectByAccountNo(String accountNo);

    CrossBorderAccount selectById(Long id);

    List<CrossBorderAccount> selectAll();

    /**
     * 按 id 批量查询。列表场景一次性取回，避免逐条查询造成的 N+1。
     */
    List<CrossBorderAccount> selectByIds(@Param("ids") java.util.Collection<Long> ids);

    int insert(CrossBorderAccount account);

    /**
     * 扣减余额。带上余额条件保证不会扣成负数，这是资金安全的基本保障。
     */
    int deduct(@Param("id") Long id, @Param("amount") BigDecimal amount, @Param("version") int version);

    int credit(@Param("id") Long id, @Param("amount") BigDecimal amount);

    int freeze(@Param("id") Long id, @Param("amount") BigDecimal amount);

    int unfreeze(@Param("id") Long id, @Param("amount") BigDecimal amount);

    int clearAll();

}
