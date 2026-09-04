package com.dong.lab.crossborder.service.impl;

import com.dong.lab.common.constant.Constants;
import com.dong.lab.common.exception.BusinessException;
import com.dong.lab.common.util.Snowflake;
import com.dong.lab.crossborder.dto.SettlementBatchResponse;
import com.dong.lab.crossborder.entity.AccountLedger;
import com.dong.lab.crossborder.entity.CrossBorderAccount;
import com.dong.lab.crossborder.entity.CrossBorderRemittance;
import com.dong.lab.crossborder.entity.SettlementBatch;
import com.dong.lab.crossborder.enums.LedgerDirection;
import com.dong.lab.crossborder.enums.RemittanceStatus;
import com.dong.lab.crossborder.enums.SettlementChannel;
import com.dong.lab.crossborder.enums.SettlementStatus;
import com.dong.lab.crossborder.mapper.CrossBorderRemittanceMapper;
import com.dong.lab.crossborder.mapper.SettlementBatchMapper;
import com.dong.lab.crossborder.service.CrossBorderLedgerService;
import com.dong.lab.crossborder.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/**
 * SettlementServiceImpl，Settlement 业务服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class SettlementServiceImpl implements SettlementService {

    /**
     * batchMapper，MyBatis Mapper 数据访问层。
     */
    private final SettlementBatchMapper batchMapper;

    /**
     * remittanceMapper，MyBatis Mapper 数据访问层。
     */
    private final CrossBorderRemittanceMapper remittanceMapper;

    /**
     * ledgerService，业务服务层。
     */
    private final CrossBorderLedgerService ledgerService;

    /**
     * snowflake。
     */
    private final Snowflake snowflake;

    /**
     * createBatch。
     */
    @Override
    public String createBatch(SettlementChannel channel, String currency, long cutoffMinutes) {
        SettlementBatch batch = new SettlementBatch();
        batch.setBatchNo("SB" + snowflake.nextId());
        batch.setChannel(channel == null ? SettlementChannel.SWIFT : channel);
        batch.setCurrency(currency);
        batch.setTotalCount(0);
        batch.setTotalAmount(BigDecimal.ZERO);
        batch.setStatus(SettlementStatus.OPEN);
        batch.setCutoffTime(LocalDateTime.now().plusMinutes(cutoffMinutes));
        batchMapper.insert(batch);
        return batch.getBatchNo();
    }

    /**
     * findByBatchNo。
     */
    @Override
    public SettlementBatchResponse findByBatchNo(String batchNo) {
        SettlementBatch batch = batchMapper.selectByBatchNo(batchNo);
        if (batch == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "batch " + batchNo + " not found");
        }
        return SettlementBatchResponse.from(batch);
    }

    /**
     * 查询全部。
     */
    @Override
    public List<SettlementBatchResponse> findAll() {
        return batchMapper.selectAll().stream().map(SettlementBatchResponse::from).toList();
    }

    /**
     * 并入批次。只有已扣款的汇款单才参与清算，
     * 未扣款或已失败的不能进入，否则会造成渠道侧多放款。
     */
    @Override
    public int collect(String batchNo, int limit) {
        SettlementBatch batch = batchMapper.selectByBatchNo(batchNo);
        if (batch == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "batch " + batchNo + " not found");
        }
        if (batch.getStatus() != SettlementStatus.OPEN) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT,
                    "batch " + batchNo + " is not open, current status " + batch.getStatus());
        }
        List<CrossBorderRemittance> candidates =
                remittanceMapper.selectByStatus(RemittanceStatus.FUNDS_DEBITED, limit);
        int count = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (CrossBorderRemittance candidate : candidates) {
            remittanceMapper.updateBatchNo(candidate.getRemittanceNo(), batchNo);
            count++;
            total = total.add(candidate.getTargetAmount());
        }
        if (count > 0) {
            batchMapper.updateTotal(batchNo, batch.getTotalCount() + count, batch.getTotalAmount().add(total));
        }
        return count;
    }

    /**
     * 关闭到期的批次。
     */
    @Override
    public int closeOverdue() {
        return batchMapper.closeOverdue(LocalDateTime.now());
    }

    /**
     * 清算并给收款方入账。入账与记流水在同一事务内，
     * 保证收款方余额与流水必定一致。
     */
    @Override
    public int settle(String batchNo) {
        SettlementBatch batch = batchMapper.selectByBatchNo(batchNo);
        if (batch == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "batch " + batchNo + " not found");
        }
        List<CrossBorderRemittance> items = remittanceMapper.selectByBatchNo(batchNo);
        int settled = 0;
        for (CrossBorderRemittance item : items) {
            if (item.getStatus() != RemittanceStatus.FUNDS_DEBITED) {
                continue;
            }
            ledgerService.creditAndAdvance(item);
            settled++;
        }
        batchMapper.updateStatus(batchNo, SettlementStatus.SETTLED);
        return settled;
    }

    /**
     * 清空全部数据，仅测试场景使用。
     */
    @Override
    public int clearAll() {
        return batchMapper.clearAll();
    }

}
