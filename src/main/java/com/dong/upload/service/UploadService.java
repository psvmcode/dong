package com.dong.upload.service;

import com.dong.common.result.PageResult;
import com.dong.upload.dto.UploadInitRequest;
import com.dong.upload.dto.UploadInitResponse;
import com.dong.upload.dto.UploadStatusResponse;
import com.dong.upload.dto.UploadTaskQuery;
import com.dong.upload.dto.UploadTaskResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件分片上传服务。
 *
 * <p>对外只暴露四个动作：初始化（含秒传与续传判定）、收分片、合并、查进度。
 * 断点续传的能力集中在 init 的返回值里——把「已经收过哪些分片」如实告诉前端，
 * 由前端决定补哪些，服务端不替前端记进度。
 */
public interface UploadService {

    /**
     * 初始化上传任务，返回是否需要秒传、以及已接收的分片下标。
     *
     * @param request 初始化请求
     * @return 初始化结果
     */
    UploadInitResponse init(UploadInitRequest request);

    /**
     * 接收一个分片，重复上传同一片是幂等的。
     *
     * @param uploadId   上传任务号
     * @param chunkIndex 分片下标
     * @param chunkHash  分片指纹
     * @param file       分片内容
     * @return 分片字节数
     */
    long receiveChunk(String uploadId, int chunkIndex, String chunkHash, MultipartFile file);

    /**
     * 合并所有分片并标记任务完成。
     *
     * @param uploadId 上传任务号
     * @return 合并后的相对存储路径，未落盘为空串
     */
    String complete(String uploadId);

    /**
     * 查询上传进度。
     *
     * @param uploadId 上传任务号
     * @return 进度信息
     */
    UploadStatusResponse status(String uploadId);

    /**
     * 分页查询上传任务。
     *
     * @param query 查询条件
     * @return 任务分页结果
     */
    PageResult<UploadTaskResponse> page(UploadTaskQuery query);

    /**
     * 取消上传任务，清理分片记录与已落盘文件。
     *
     * @param uploadId 上传任务号
     */
    void cancel(String uploadId);

    /**
     * 清理超过指定小时数仍未完成的任务。
     *
     * @param hours 过期小时数
     * @param limit 单次最多处理条数
     * @return 清理掉的任务数
     */
    int cleanupExpired(int hours, int limit);

}
