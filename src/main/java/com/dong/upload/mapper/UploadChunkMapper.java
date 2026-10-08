package com.dong.upload.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 已接收分片数据访问层。
 *
 * <p>只记元信息，不存二进制内容。特大文件会切出上万条，
 * 每条一行，靠 uk_upload_chunk 保证同一分片重复上传不会记两遍。
 */
@Mapper
public interface UploadChunkMapper {

    /**
     * 新增分片记录，重复上传同一片时覆盖字节数与指纹。
     *
     * <p>用 insert ... on duplicate key update 而不是先查后写：
     * 并发收同一个分片时，先查后写会有窗口期插进两条，唯一键直接报冲突。
     *
     * @param uploadId   上传任务号
     * @param chunkIndex 分片下标
     * @param chunkSize  分片字节数
     * @param chunkHash  分片指纹
     * @return 影响行数，1 表示新增，2 表示覆盖
     */
    int upsert(@Param("uploadId") String uploadId, @Param("chunkIndex") Integer chunkIndex, @Param("chunkSize") Long chunkSize, @Param("chunkHash") String chunkHash);

    /**
     * 查询某任务已接收的分片下标。
     *
     * @param uploadId 上传任务号
     * @return 分片下标列表，按升序
     */
    List<Integer> selectChunkIndexes(@Param("uploadId") String uploadId);

    /**
     * 统计某任务已接收分片数。
     *
     * @param uploadId 上传任务号
     * @return 分片数
     */
    long countByUploadId(@Param("uploadId") String uploadId);

    /**
     * 累加某任务已接收字节数。
     *
     * @param uploadId 上传任务号
     * @return 总字节数，没有分片时返回 0
     */
    Long sumSizeByUploadId(@Param("uploadId") String uploadId);

    /**
     * 删除某任务的全部分片记录。
     *
     * @param uploadId 上传任务号
     * @return 影响行数
     */
    int deleteByUploadId(@Param("uploadId") String uploadId);

}
