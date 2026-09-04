package com.dong.lab.tcc.mapper;

import com.dong.lab.tcc.entity.TccBranch;
import com.dong.lab.tcc.entity.TccTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * TccTransactionMapper，MyBatis 数据访问接口。
 */
@Mapper

public interface TccTransactionMapper {

    /**
     * 按 Xid 查询记录。
     */
    TccTransaction selectByXid(@Param("xid") String xid);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(TccTransaction transaction);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("xid") String xid, @Param("status") int status);

    /**
     * 按 Status 查询记录。
     */
    List<TccTransaction> selectByStatus(@Param("status") int status, @Param("limit") int limit);

    /**
     * 查询单条分支记录。
     */
    TccBranch selectBranch(@Param("xid") String xid, @Param("branchId") String branchId);

    /**
     * 插入分支事务记录。
     */
    int insertBranch(TccBranch branch);

    /**
     * 更新分支事务状态。
     */
    int updateBranchStatus(@Param("xid") String xid,
                           @Param("branchId") String branchId,
                           @Param("status") int status,
                           @Param("errorMessage") String errorMessage);

    /**
     * 查询分支记录。
     */
    List<TccBranch> selectBranches(@Param("xid") String xid);

    /**
     * 查询可重试的分支记录。
     */
    List<TccBranch> selectRetryableBranches(@Param("status") int status, @Param("limit") int limit);

    /**
     * 清空全部数据，仅测试场景使用。
     */
    int clearAll();

}
