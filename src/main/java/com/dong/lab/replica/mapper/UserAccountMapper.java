package com.dong.lab.replica.mapper;

import com.dong.lab.replica.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAccountMapper {

    UserAccount selectByUserId(@Param("userId") Long userId);

    List<UserAccount> selectAll();

    int insert(UserAccount userAccount);

    int updateBalance(@Param("userId") Long userId, @Param("balance") long balance);

}
