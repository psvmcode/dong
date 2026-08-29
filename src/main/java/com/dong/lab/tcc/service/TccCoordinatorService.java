package com.dong.lab.tcc.service;

import com.dong.lab.tcc.dto.TccOrderRequest;
import com.dong.lab.tcc.dto.TccResultResponse;
import com.dong.lab.tcc.entity.TccBranch;

import java.util.List;
import java.util.Map;

public interface TccCoordinatorService {

    TccResultResponse submit(TccOrderRequest request);

    Map<String, Object> status(String xid);

    int recoverPending();

    List<TccBranch> branches(String xid);

    void seed(Long userId, Long productId, int available, long balance);

}
