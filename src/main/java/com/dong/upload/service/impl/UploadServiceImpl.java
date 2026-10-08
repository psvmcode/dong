package com.dong.upload.service.impl;

import com.dong.common.constant.Constants;
import com.dong.common.exception.BusinessException;
import com.dong.common.result.PageResult;
import com.dong.upload.dto.UploadInitRequest;
import com.dong.upload.dto.UploadInitResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    @Transactional(rollbackFor = Exception.class)
    public UploadInitResponse init(UploadInitRequest request) {
        UploadTask completed = uploadTaskMapper.selectCompletedByHash(request.getFileHash(), request.getFileSize());
        if (completed != null) {
            log.info("upload instant hit uploadId={} hash={}", completed.getUploadId(), request.getFileHash());
            return instantResponse(completed);
        }
        int chunkSize = request.getChunkSize() == null ? defaultChunkSize : request.getChunkSize();
        int totalChunks = (int) ((request.getFileSize() + chunkSize - 1) / chunkSize);
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
    @Transactional(rollbackFor = Exception.class)
    public long receiveChunk(String uploadId, int chunkIndex, String chunkHash, MultipartFile file) {
        UploadTask task = requireTask(uploadId);
        if (UploadStatus.of(task.getStatus()).isFinal()) {
            throw new BusinessException(Constants.CODE_OPERATION_CONFLICT, "upload task already finished");
        }
        if (chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID, "chunk index out of range");
        }
        long size;
        try {
            size = uploadStorage.writeChunk(uploadId, chunkIndex, file);
        } catch (IOException ex) {
            log.error("upload chunk write failed uploadId={} index={}", uploadId, chunkIndex, ex);
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "chunk storage unavailable");
        }
        uploadChunkMapper.upsert(uploadId, chunkIndex, size, chunkHash == null ? "" : chunkHash);
        return size;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String complete(String uploadId) {
        UploadTask task = requireTask(uploadId);
        if (UploadStatus.of(task.getStatus()) == UploadStatus.COMPLETED) {
            return task.getStoragePath();
        }
        List<Integer> indexes = uploadChunkMapper.selectChunkIndexes(uploadId);
        if (indexes.size() < task.getTotalChunks()) {
            throw new BusinessException(Constants.CODE_PARAM_INVALID,
                    "chunks incomplete, received " + indexes.size() + " of " + task.getTotalChunks());
        }
        String path;
        try {
            path = uploadStorage.merge(uploadId, task.getFileName(), indexes);
        } catch (IOException ex) {
            log.error("upload merge failed uploadId={}", uploadId, ex);
            throw new BusinessException(Constants.CODE_DEPENDENCY_UNAVAILABLE, "merge failed");
        }
        uploadTaskMapper.updateStatus(uploadId, UploadStatus.COMPLETED.getCode(), path);
        uploadStorage.deleteChunks(uploadId);
        log.info("upload completed uploadId={} chunks={} path={}", uploadId, indexes.size(), path);
        return path;
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
        List<UploadTaskResponse> rows = new ArrayList<>(list.size());
        for (UploadTask task : list) {
            rows.add(toResponse(task));
        }
        return PageResult.of(rows, total, query.toPageRequest());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String uploadId) {
        UploadTask task = requireTask(uploadId);
        uploadTaskMapper.updateStatus(uploadId, UploadStatus.CANCELLED.getCode(), "");
        uploadChunkMapper.deleteByUploadId(uploadId);
        uploadStorage.deleteChunks(uploadId);
        uploadStorage.deleteTaskFile(task.getStoragePath());
        log.info("upload cancelled uploadId={}", uploadId);
    }

    @Override
    public int cleanupExpired(int hours, int limit) {
        List<UploadTask> expired = uploadTaskMapper.selectExpired(hours, limit);
        for (UploadTask task : expired) {
            uploadChunkMapper.deleteByUploadId(task.getUploadId());
            uploadTaskMapper.deleteByUploadId(task.getUploadId());
            uploadStorage.deleteChunks(task.getUploadId());
        }
        if (!expired.isEmpty()) {
            log.info("upload cleanup expired removed={} hours={}", expired.size(), hours);
        }
        return expired.size();
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
     * @param request     初始化请求
     * @param chunkSize   分片大小
     * @param totalChunks 总分片数
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
        uploadTaskMapper.insert(task);
        log.info("upload task created uploadId={} chunks={} size={}", task.getUploadId(), totalChunks, request.getFileSize());
        return task;
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
     * 实体转列表响应，指纹在抽样模式下截断展示。
     *
     * @param task 任务实体
     * @return 列表响应
     */
    private UploadTaskResponse toResponse(UploadTask task) {
        UploadTaskResponse response = new UploadTaskResponse();
        response.setUploadId(task.getUploadId());
        response.setFileName(task.getFileName());
        response.setFileSize(task.getFileSize());
        response.setChunkSize(task.getChunkSize());
        response.setTotalChunks(task.getTotalChunks());
        response.setUploadedChunks((int) uploadChunkMapper.countByUploadId(task.getUploadId()));
        response.setFileHash(task.getFileHash());
        response.setHashMode(task.getHashMode());
        response.setStatus(task.getStatus());
        response.setStatusName(UploadStatus.of(task.getStatus()).name());
        response.setCreateTime(String.valueOf(task.getCreateTime()));
        return response;
    }

}
