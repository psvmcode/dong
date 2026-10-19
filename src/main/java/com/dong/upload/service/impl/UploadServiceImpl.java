package com.dong.upload.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageResult;
import com.dong.upload.dto.UploadChunkCount;
import com.dong.upload.dto.UploadInitRequest;
import com.dong.upload.dto.UploadInitResponse;
import com.dong.upload.dto.UploadMergeResult;
import com.dong.upload.dto.UploadStatusResponse;
import com.dong.upload.dto.UploadTaskQuery;
import com.dong.upload.dto.UploadTaskResponse;
import com.dong.upload.entity.UploadTask;
import com.dong.upload.enums.UploadStatus;
import com.dong.upload.mapper.UploadChunkMapper;
import com.dong.upload.mapper.UploadTaskMapper;
import com.dong.upload.service.UploadService;
import com.dong.upload.support.UploadIdGenerator;
import com.dong.upload.support.UploadStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件分片上传服务实现。
 *
 * <p>断点续传的全部秘密在 init 的返回值里：
 * 把「这个任务已经收过哪些分片」如实告诉前端，前端跳过它们只补缺失的。
 * 服务端不做「记住客户端进度」这件事，因为客户端可能换浏览器、换机器，
 * 唯一可靠的服务端状态就是分片记录本身。
 *
 * <p>秒传判定用「指纹 + 文件大小」联合：
 * 抽样指纹只算首尾加中间几块，两块内容不同的大文件有可能撞上，
 * 加上大小一起判，碰撞概率才降到可接受。
 *
 * <p><b>这一层的写操作刻意不加 @Transactional</b>，两个原因：
 * <ul>
 *   <li>每个写操作都是单条且幂等的（upsert 分片、touch 任务、带条件更新状态），
 *       没有跨表一致性要维护，事务带来的只有长连接占用。</li>
 *   <li>收分片要走磁盘 IO，包在事务里会让一个连接被几十毫秒的写盘拖住，
 *       并发上传时连接池会先于磁盘被打满。IO 失败也不该回滚已经写进去的东西，
 *       失败就是失败，重试交给前端。</li>
 * </ul>
 * 状态推进的正确性不靠事务，靠 SQL 里的条件（见 updateStatusFromUploading）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    /**
     * uploadTaskMapper，MyBatis Mapper 数据访问层。
     */
    private final UploadTaskMapper uploadTaskMapper;

    /**
     * uploadChunkMapper，MyBatis Mapper 数据访问层。
     */
    private final UploadChunkMapper uploadChunkMapper;

    /**
     * uploadStorage，落盘组件，开关关闭时只记元信息。
     */
    private final UploadStorage uploadStorage;

    /**
     * uploadIdGenerator，上传任务号生成器。
     */
    private final UploadIdGenerator uploadIdGenerator;

    /**
     * 默认分片大小，前端不指定时用配置值。
     */
    @Value("${dong.upload.chunk-size:5242880}")
    private int defaultChunkSize;

    @Override
    public UploadInitResponse init(UploadInitRequest request) {
        UploadTask completed = uploadTaskMapper.selectCompletedByHash(request.getFileHash(), request.getFileSize());
        if (completed != null) {
            log.info("upload instant hit uploadId={} hash={}", completed.getUploadId(), request.getFileHash());
            return instantResponse(completed);
        }
        int chunkSize = request.getChunkSize() == null ? defaultChunkSize : request.getChunkSize();
        int totalChunks = (int) ((request.getFileSize() + chunkSize - 1) / chunkSize);
        if (totalChunks > Constants.MAX_CHUNK_COUNT) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "too many chunks, " + totalChunks + " exceeds " + Constants.MAX_CHUNK_COUNT);
        }
        String uploadId = uploadIdGenerator.resolve(request.getFileHash(), request.getFileSize(), request.getFileName(), chunkSize);
        UploadTask existed = uploadTaskMapper.selectByUploadId(uploadId);
        UploadTask task = existed == null ? createTask(request, chunkSize, totalChunks, uploadId) : existed;
        UploadInitResponse response = new UploadInitResponse();
        response.setUploadId(task.getUploadId());
        response.setInstant(false);
        response.setTotalChunks(totalChunks);
        response.setUploadedChunks(uploadChunkMapper.selectChunkIndexes(task.getUploadId()));
        response.setStatus(task.getStatus());
        return response;
    }

    @Override
    public long receiveChunk(String uploadId, int chunkIndex, String chunkHash, MultipartFile file) {
        UploadTask task = requireTask(uploadId);
        UploadStatus status = UploadStatus.of(task.getStatus());
        if (status.isFinal()) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "upload task already finished");
        }
        if (chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "chunk index out of range");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "chunk file is empty");
        }
        // 分片大小必须不超过任务声明的 chunkSize。少了这一条，
        // 前端可以拿同一个 uploadId 持续投递超大分片，把磁盘写满
        if (file.getSize() > task.getChunkSize()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "chunk too large, " + file.getSize() + " exceeds " + task.getChunkSize());
        }
        long size;
        try {
            size = uploadStorage.writeChunk(uploadId, chunkIndex, file);
        } catch (IOException ex) {
            log.error("upload chunk write failed uploadId={} index={}", uploadId, chunkIndex, ex);
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "chunk storage unavailable");
        }
        uploadChunkMapper.upsert(uploadId, chunkIndex, size, chunkHash == null ? "" : chunkHash);
        // 推进 update_time，否则传一个大文件传过 24 小时后会被清理任务当成过期任务删掉
        uploadTaskMapper.touch(uploadId);
        return size;
    }

    @Override
    public String complete(String uploadId) {
        UploadTask task = requireTask(uploadId);
        UploadStatus status = UploadStatus.of(task.getStatus());
        if (status == UploadStatus.COMPLETED) {
            return task.getStoragePath();
        }
        if (status.isFinal()) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "upload task is cancelled");
        }
        List<Integer> indexes = uploadChunkMapper.selectChunkIndexes(uploadId);
        if (indexes.size() < task.getTotalChunks()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "chunks incomplete, received " + indexes.size() + " of " + task.getTotalChunks());
        }
        UploadMergeResult merged;
        try {
            merged = uploadStorage.merge(uploadId, task.getFileName(), indexes);
        } catch (IOException ex) {
            log.error("upload merge failed uploadId={}", uploadId, ex);
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "merge failed");
        }
        // 只在真落盘时校验：开关关闭时 bytes 恒为 0，没有可比的对象
        if (merged.bytes() > 0 && merged.bytes() != task.getFileSize()) {
            log.error("upload merge size mismatch uploadId={} merged={} declared={}", uploadId, merged.bytes(), task.getFileSize());
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "merged size mismatch");
        }
        // 用 where status = 0 做乐观锁：并发 complete 时只有一个请求能推进成功
        int rows = uploadTaskMapper.updateStatusFromUploading(
                uploadId, UploadStatus.COMPLETED.getCode(), merged.path(), task.getFileSize());
        if (rows == 0) {
            // 落败说明被并发请求抢先，或被并发取消；直接把当前状态的结果返回，重试没有意义
            return requireTask(uploadId).getStoragePath();
        }
        uploadStorage.deleteChunks(uploadId);
        log.info("upload completed uploadId={} chunks={} path={}", uploadId, indexes.size(), merged.path());
        return merged.path();
    }

    @Override
    public UploadStatusResponse status(String uploadId) {
        UploadTask task = requireTask(uploadId);
        long count = uploadChunkMapper.countByUploadId(uploadId);
        Long bytes = uploadChunkMapper.sumSizeByUploadId(uploadId);
        long uploadedBytes = bytes == null ? 0L : bytes;
        UploadStatusResponse response = new UploadStatusResponse();
        response.setUploadId(task.getUploadId());
        response.setFileName(task.getFileName());
        response.setFileSize(task.getFileSize());
        response.setTotalChunks(task.getTotalChunks());
        response.setUploadedChunks((int) count);
        response.setUploadedBytes(uploadedBytes);
        response.setPercent(task.getFileSize() == null || task.getFileSize() == 0
                ? 0
                : (int) Math.min(100, uploadedBytes * 100 / task.getFileSize()));
        response.setStatus(task.getStatus());
        response.setStatusName(UploadStatus.of(task.getStatus()).name());
        return response;
    }

    @Override
    public PageResult<UploadTaskResponse> page(UploadTaskQuery query) {
        int offset = (query.getPageNum() - 1) * query.getPageSize();
        List<UploadTask> list = uploadTaskMapper.selectByPage(query.getStatus(), offset, query.getPageSize());
        long total = uploadTaskMapper.countByStatus(query.getStatus());
        Map<String, Long> counts = loadChunkCounts(list);
        List<UploadTaskResponse> rows = new ArrayList<>(list.size());
        for (UploadTask task : list) {
            rows.add(toResponse(task, counts.getOrDefault(task.getUploadId(), 0L)));
        }
        return PageResult.of(rows, total, query.toPageRequest());
    }

    @Override
    public void cancel(String uploadId) {
        UploadTask task = requireTask(uploadId);
        if (UploadStatus.of(task.getStatus()) == UploadStatus.COMPLETED) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "upload task already completed");
        }
        uploadTaskMapper.updateStatus(uploadId, UploadStatus.CANCELLED.getCode(), "", 0L);
        uploadChunkMapper.deleteByUploadId(uploadId);
        uploadStorage.deleteChunks(uploadId);
        uploadStorage.deleteTaskFile(task.getStoragePath());
        uploadStorage.deleteTaskDir(uploadId);
        log.info("upload cancelled uploadId={}", uploadId);
    }

    @Override
    public int cleanupExpired(int hours, int limit) {
        int safeHours = Math.max(1, hours);
        int safeLimit = Math.min(Math.max(1, limit), Constants.MAX_BATCH_SIZE);
        if (safeLimit < limit) {
            log.warn("upload cleanup limit truncated from {} to {}", limit, safeLimit);
        }
        List<UploadTask> expired = uploadTaskMapper.selectExpired(safeHours, safeLimit);
        int removed = 0;
        for (UploadTask task : expired) {
            try {
                uploadChunkMapper.deleteByUploadId(task.getUploadId());
                uploadTaskMapper.deleteByUploadId(task.getUploadId());
                uploadStorage.deleteChunks(task.getUploadId());
                removed += 1;
            } catch (Exception ex) {
                // 单条失败不能中断整批：清理是旁路任务，卡在一条上会让后面的全部积压到下一轮
                log.warn("upload cleanup failed uploadId={} reason={}", task.getUploadId(), ex.getMessage());
            }
        }
        if (removed > 0) {
            log.info("upload cleanup expired removed={} hours={}", removed, safeHours);
        }
        return removed;
    }

    /**
     * 命中秒传时构造响应。
     *
     * @param completed 已完成的同指纹任务
     * @return 初始化响应
     */
    private UploadInitResponse instantResponse(UploadTask completed) {
        UploadInitResponse response = new UploadInitResponse();
        response.setUploadId(completed.getUploadId());
        response.setInstant(true);
        response.setTotalChunks(completed.getTotalChunks());
        response.setUploadedChunks(new ArrayList<>());
        response.setStatus(completed.getStatus());
        return response;
    }

    /**
     * 创建上传任务。
     *
     * <p>并发 init 同一个文件时，两个请求都会查不到任务、都走到 insert，
     * 唯一键必然冲突一个。冲突不是错误——它说明别人已经建好了，
     * 查回来复用即可，让请求报 5000 才是错的。
     *
     * @param request     初始化请求
     * @param chunkSize   分片大小
     * @param totalChunks 总分片数
     * @param uploadId    上传任务号
     * @return 已落库的任务
     */
    private UploadTask createTask(UploadInitRequest request, int chunkSize, int totalChunks, String uploadId) {
        UploadTask task = new UploadTask();
        task.setUploadId(uploadId);
        task.setFileName(request.getFileName());
        task.setFileSize(request.getFileSize());
        task.setChunkSize(chunkSize);
        task.setTotalChunks(totalChunks);
        task.setFileHash(request.getFileHash());
        task.setHashMode(request.getHashMode());
        task.setStatus(UploadStatus.UPLOADING.getCode());
        task.setStoragePath("");
        task.setUploadedBytes(0L);
        try {
            uploadTaskMapper.insert(task);
        } catch (DuplicateKeyException ex) {
            log.info("upload task concurrent create, reuse uploadId={}", uploadId);
            UploadTask existed = uploadTaskMapper.selectByUploadId(uploadId);
            if (existed == null) {
                throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "upload task create conflict");
            }
            return existed;
        }
        log.info("upload task created uploadId={} chunks={} size={}", uploadId, totalChunks, request.getFileSize());
        return task;
    }

    /**
     * 批量取各任务已收分片数，避免列表查询逐条 count。
     *
     * @param list 当前页任务
     * @return 任务号到分片数的映射，无分片的任务不在映射里
     */
    private Map<String, Long> loadChunkCounts(List<UploadTask> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        List<String> ids = new ArrayList<>(list.size());
        for (UploadTask task : list) {
            ids.add(task.getUploadId());
        }
        List<UploadChunkCount> counts = uploadChunkMapper.countByUploadIds(ids);
        Map<String, Long> map = new HashMap<>(counts.size());
        for (UploadChunkCount item : counts) {
            map.put(item.getUploadId(), item.getCount());
        }
        return map;
    }

    /**
     * 取任务，不存在抛 1001。
     *
     * @param uploadId 上传任务号
     * @return 任务实体
     */
    private UploadTask requireTask(String uploadId) {
        UploadTask task = uploadTaskMapper.selectByUploadId(uploadId);
        if (task == null) {
            throw new BusinessException(Constants.CODE_DATA_NOT_FOUND, "upload task not found");
        }
        return task;
    }

    /**
     * 实体转列表响应。
     *
     * @param task          任务实体
     * @param uploadedCount 已收分片数，由批量查询带进来
     * @return 列表响应
     */
    private UploadTaskResponse toResponse(UploadTask task, long uploadedCount) {
        UploadTaskResponse response = new UploadTaskResponse();
        response.setUploadId(task.getUploadId());
        response.setFileName(task.getFileName());
        response.setFileSize(task.getFileSize());
        response.setChunkSize(task.getChunkSize());
        response.setTotalChunks(task.getTotalChunks());
        response.setUploadedChunks((int) uploadedCount);
        response.setFileHash(task.getFileHash());
        response.setHashMode(task.getHashMode());
        response.setStatus(task.getStatus());
        response.setStatusName(UploadStatus.of(task.getStatus()).name());
        response.setCreateTime(String.valueOf(task.getCreateTime()));
        return response;
    }

}
