package com.dong.lab.tcc.mapper;

import com.dong.lab.tcc.entity.TccBranch;
import com.dong.lab.tcc.entity.TccTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TccTransactionMapper {

    TccTransaction selectByXid(@Param("xid") String xid);

    int insert(TccTransaction transaction);

    int updateStatus(@Param("xid") String xid, @Param("status") int status);

    List<TccTransaction> selectByStatus(@Param("status") int status, @Param("limit") int limit);

    TccBranch selectBranch(@Param("xid") String xid, @Param("branchId") String branchId);

    int insertBranch(TccBranch branch);

    int updateBranchStatus(@Param("xid") String xid,
                           @Param("branchId") String branchId,
                           @Param("status") int status,
                           @Param("errorMessage") String errorMessage);

    List<TccBranch> selectBranches(@Param("xid") String xid);

    List<TccBranch> selectRetryableBranches(@Param("status") int status, @Param("limit") int limit);

    int clearAll();

}
