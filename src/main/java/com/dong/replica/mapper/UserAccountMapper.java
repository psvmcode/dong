package com.dong.replica.mapper;

import com.dong.replica.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * UserAccountMapper，MyBatis 数据访问接口。
 */
@Mapper
public interface UserAccountMapper {

    /**
     * 按 UserId 查询记录。
     */
    UserAccount selectByUserId(@Param("userId") Long userId);

    /**
     * 查询账户列表，SQL 层按上限截断，避免先把整表读进内存再在 Java 侧截断。
     *
     * @param limit 最大条数
     * @return 账户列表
     */
    List<UserAccount> selectAll(@Param("limit") int limit);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(UserAccount userAccount);

    /**
     * 更新余额。
     */
    int updateBalance(@Param("userId") Long userId, @Param("balance") long balance);

}
