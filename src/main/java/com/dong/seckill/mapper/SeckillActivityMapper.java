package com.dong.seckill.mapper;

import com.dong.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SeckillActivityMapper，MyBatis 数据访问接口。
 */
@Mapper
public interface SeckillActivityMapper {

    /**
     * 根据 id 查询记录。
     */
    SeckillActivity selectById(@Param("id") Long id);

    /**
     * 查询活动列表，SQL 层按上限截断，避免先把整表读进内存再在 Java 侧截断。
     *
     * @param limit 最大条数
     * @return 活动列表
     */
    List<SeckillActivity> selectAll(@Param("limit") int limit);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(SeckillActivity activity);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("id") Long id, @Param("status") int status, @Param("expectedVersion") int expectedVersion);

    /**
     * 更新可用库存。
     */
    int updateAvailableStock(@Param("id") Long id, @Param("availableStock") int availableStock);

}
