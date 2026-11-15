package com.dong.ad.mapper;

import com.dong.ad.entity.AdCreative;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 广告创意数据访问接口。
 */
@Mapper
public interface AdCreativeMapper {

    /**
     * 根据 id 查询创意。
     *
     * @param id 主键
     * @return 创意
     */
    AdCreative selectById(@Param("id") Long id);

    /**
     * 按单元 id 列表批量查已过审创意。
     *
     * @param unitIds 单元 id 列表
     * @param status  审核状态
     * @param limit   最大条数
     * @return 创意列表
     */
    List<AdCreative> selectApprovedByUnitIds(@Param("unitIds") List<Long> unitIds, @Param("status") int status, @Param("limit") int limit);

    /**
     * 分页查询创意。
     *
     * @param unitId  单元过滤
     * @param status  状态过滤
     * @param keyword 标题关键字
     * @param offset  偏移量
     * @param size    每页大小
     * @return 创意列表
     */
    List<AdCreative> selectByPage(@Param("unitId") Long unitId, @Param("status") Integer status, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计分页查询总数。
     *
     * @param unitId  单元过滤
     * @param status  状态过滤
     * @param keyword 标题关键字
     * @return 总数
     */
    long countByPage(@Param("unitId") Long unitId, @Param("status") Integer status, @Param("keyword") String keyword);

    /**
     * 统计单元下的过审创意数，计划上线守卫使用。
     *
     * @param unitId 单元 id
     * @param status 审核状态
     * @return 创意数
     */
    long countByUnit(@Param("unitId") Long unitId, @Param("status") int status);

    /**
     * 查询单元下全部创意。
     *
     * @param unitId 单元 id
     * @param limit  最大条数
     * @return 创意列表
     */
    List<AdCreative> selectByUnit(@Param("unitId") Long unitId, @Param("limit") int limit);

    /**
     * 新增创意。
     *
     * @param creative 创意实体
     * @return 影响行数
     */
    int insert(AdCreative creative);

    /**
     * 条件推进审核状态，带版本号防止并发审核互相覆盖。
     *
     * @param id              创意 id
     * @param status          目标状态
     * @param rejectReason    驳回原因
     * @param expectedVersion 期望版本号
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") int status, @Param("rejectReason") String rejectReason, @Param("expectedVersion") int expectedVersion);

}
