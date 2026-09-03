package com.dong.lab.crossborder.mapper;

import com.dong.lab.crossborder.entity.AccountEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账户事件数据访问。事件只增不改：留痕数据的本质是审计证据，
 * 提供更新接口反而会引入被篡改的风险。
 */
@Mapper
public interface AccountEventMapper {

    /**
     * 按账号查询事件，时间正序排列，还原账户状态变化的历史脉络。
     */
    List<AccountEvent> selectByAccountNo(@Param("accountNo") String accountNo);

    int insert(AccountEvent event);

    int clearAll();

}
