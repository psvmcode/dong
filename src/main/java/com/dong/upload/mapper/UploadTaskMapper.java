package com.dong.upload.mapper;

import com.dong.upload.entity.UploadTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 上传任务数据访问层。
 */
@Mapper
public interface UploadTaskMapper {

    /**
     * 新增上传任务，主键回填到 id。
     *
     * @param task 任务实体
     * @return 影响行数
     */
    int insert(UploadTask task);

    /**
     * 按任务号查询。
     *
     * @param uploadId 上传任务号
     * @return 任务实体，不存在返回 null
     */
    UploadTask selectByUploadId(@Param("uploadId") String uploadId);

    /**
     * 按指纹与文件大小查询已完成任务，用于秒传判定。
     *
     * @param fileHash 文件指纹
     * @param fileSize 文件大小
     * @return 已完成任务，不存在返回 null
     */
    UploadTask selectCompletedByHash(@Param("fileHash") String fileHash, @Param("fileSize") Long fileSize);

    /**
     * 推进已上传字节数，用累加而不是覆盖，避免并发收片时互相覆盖。
     *
     * @param uploadId 上传任务号
     * @param delta    本次分片字节数
     * @return 影响行数
     */
    int increaseUploadedBytes(@Param("uploadId") String uploadId, @Param("delta") Long delta);

    /**
     * 更新任务状态与存储路径。
     *
     * @param uploadId    上传任务号
     * @param status      目标状态
     * @param storagePath 合并后的相对路径，未落盘传空串
     * @return 影响行数
     */
    int updateStatus(@Param("uploadId") String uploadId, @Param("status") Integer status, @Param("storagePath") String storagePath);

    /**
     * 分页查询任务列表。
     *
     * @param status  状态过滤，null 表示不过滤
     * @param offset  偏移量
     * @param size    每页条数
     * @return 任务列表
     */
    List<UploadTask> selectByPage(@Param("status") Integer status, @Param("offset") Integer offset, @Param("size") Integer size);

    /**
     * 统计任务总数。
     *
     * @param status 状态过滤，null 表示不过滤
     * @return 总数
     */
    long countByStatus(@Param("status") Integer status);

    /**
     * 查询超过指定小时数仍未完成的任务，供清理任务使用。
     *
     * @param hours 过期小时数
     * @param limit 单次最多处理条数
     * @return 待清理任务列表
     */
    List<UploadTask> selectExpired(@Param("hours") Integer hours, @Param("limit") Integer limit);

    /**
     * 删除任务记录。
     *
     * @param uploadId 上传任务号
     * @return 影响行数
     */
    int deleteByUploadId(@Param("uploadId") String uploadId);

}
