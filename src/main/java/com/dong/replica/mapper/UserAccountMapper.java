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
     * 查询所有记录。
     */
    List<UserAccount> selectAll();

    /**
     * 插入记录，返回影响行数。
     */
    int insert(UserAccount userAccount);

    /**
     * 更新余额。
     */
    int updateBalance(@Param("userId") Long userId, @Param("balance") long balance);

}
