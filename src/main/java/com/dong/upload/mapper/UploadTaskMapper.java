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
     * 更新任务状态、存储路径与已上传字节数。
     *
     * <p>已上传字节数只在终态回填：上传过程中它并不权威——
     * 并发收片时「累加」会有覆盖竞争，「读出来再加」又有窗口期，
     * 而分片记录本身是精确的（sum(chunk_size)），进度一律以它为准。
     * 这里回填是为了让终态记录能自解释「这个任务最终有多大」。
     *
     * @param uploadId      上传任务号
     * @param status        目标状态
     * @param storagePath   合并后的相对路径，未落盘传空串
     * @param uploadedBytes 终态回填的字节数，取消传 0
     * @return 影响行数，0 表示任务不存在或已被并发推进过
     */
    int updateStatus(@Param("uploadId") String uploadId, @Param("status") Integer status,
                     @Param("storagePath") String storagePath, @Param("uploadedBytes") Long uploadedBytes);

    /**
     * 只推进 update_time，用来标记「这个任务还活着」。
     *
     * <p>没有它，清理任务会把正在上传的任务误删：
     * 分片写的是 upload_chunk，upload_task 的 update_time 会一直停在创建时间，
     * 传一个大文件传了 25 小时，任务就正好落在「24 小时未完成」的清理范围内。
     *
     * @param uploadId 上传任务号
     * @return 影响行数
     */
    int touch(@Param("uploadId") String uploadId);

    /**
     * 把状态从「上传中」推进到目标状态，用 where 里的状态做乐观锁。
     *
     * <p>并发 complete 时两个请求都会读到 UPLOADING，只靠内存判断挡不住第二个。
     * 把 status = 0 写进 where，靠影响行数判定谁抢到了，落败的那个直接返回当前状态即可。
     *
     * @param uploadId      上传任务号
     * @param status        目标状态
     * @param storagePath   存储路径
     * @param uploadedBytes 终态字节数
     * @return 影响行数，1 表示抢到，0 表示已被别人推进
     */
    int updateStatusFromUploading(@Param("uploadId") String uploadId, @Param("status") Integer status,
                                  @Param("storagePath") String storagePath, @Param("uploadedBytes") Long uploadedBytes);

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
