package com.dong.ad.service.impl;

import com.dong.ad.entity.AdChargeArchive;
import com.dong.ad.entity.AdChargeRecord;
import com.dong.ad.mapper.AdChargeArchiveMapper;
import com.dong.ad.mapper.AdChargeRecordMapper;
import com.dong.ad.mapper.AdEventMapper;
import com.dong.ad.service.AdCleanupService;
import com.dong.common.constant.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 在线数据生命周期管理实现。
 *
 * <p>归档顺序是「先写归档表，再删在线表」，且只删除确认已归档的 id。
 * 反过来做或无条件删除，会在归档失败时永久丢账，这种损失无法用对账补回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdCleanupServiceImpl implements AdCleanupService {

    /**
     * adChargeRecordMapper，MyBatis Mapper 数据访问层。
     */
    private final AdChargeRecordMapper adChargeRecordMapper;

    /**
     * adChargeArchiveMapper，MyBatis Mapper 数据访问层。
     */
    private final AdChargeArchiveMapper adChargeArchiveMapper;

    /**
     * adEventMapper，MyBatis Mapper 数据访问层。
     */
    private final AdEventMapper adEventMapper;

    /**
     * 归档过期流水。
     */
    @Override
    public int archiveExpiredCharge(LocalDate beforeDate, int limit) {
        LocalDateTime before = beforeDate.atStartOfDay();
        int batch = Math.min(Math.max(1, limit), Constants.MAX_BATCH_SIZE);
        List<AdChargeRecord> expired = adChargeRecordMapper.selectExpired(before, batch);
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> archivedIds = new ArrayList<>(expired.size());
        for (AdChargeRecord record : expired) {
            try {
                if (adChargeArchiveMapper.countByEventId(record.getEventId()) == 0L) {
                    adChargeArchiveMapper.insert(toArchive(record));
                }
                archivedIds.add(record.getId());
            } catch (Exception ex) {
                // 单条归档失败不能中断整批，更不能删除这条在线流水
                log.error("ad charge archive failed id={} eventId={}", record.getId(), record.getEventId(), ex);
            }
        }
        if (!archivedIds.isEmpty()) {
            adChargeRecordMapper.deleteByIds(archivedIds);
        }
        log.info("ad charge archived count={} scanned={}", archivedIds.size(), expired.size());
        return archivedIds.size();
    }

    /**
     * 删除过期事件。
     */
    @Override
    public int deleteExpiredEvents(LocalDate beforeDate, int limit) {
        LocalDateTime before = LocalDateTime.of(beforeDate, LocalTime.MAX);
        int batch = Math.min(Math.max(1, limit), Constants.MAX_BATCH_SIZE);
        List<Long> ids = adEventMapper.selectIdsBefore(before, batch);
        if (ids.isEmpty()) {
            return 0;
        }
        int removed = adEventMapper.deleteByIds(ids);
        log.info("ad expired events removed count={}", removed);
        return removed;
    }

    /**
     * 在线流水转归档行。create_time 保留原值，便于按事件日期追溯。
     *
     * @param record 在线流水
     * @return 归档行
     */
    private AdChargeArchive toArchive(AdChargeRecord record) {
        AdChargeArchive archive = new AdChargeArchive();
        archive.setEventId(record.getEventId());
        archive.setTraceId(record.getTraceId());
        archive.setStatDate(record.getStatDate());
        archive.setCampaignId(record.getCampaignId());
        archive.setAdvertiserId(record.getAdvertiserId());
        archive.setEventType(record.getEventType());
        archive.setAmountCents(record.getAmountCents());
        archive.setStatus(record.getStatus());
        archive.setChargeMode(record.getChargeMode());
        archive.setCreateTime(record.getCreateTime());
        return archive;
    }

}
