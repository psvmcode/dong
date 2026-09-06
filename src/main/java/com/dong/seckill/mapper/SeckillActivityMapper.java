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
     * 查询所有记录。
     */
    List<SeckillActivity> selectAll();

    /**
     * 插入记录，返回影响行数。
     */
    int insert(SeckillActivity activity);

    /**
     * 更新状态，返回影响行数。
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") int status,
                     @Param("expectedVersion") int expectedVersion);

    /**
     * 更新可用库存。
     */
    int updateAvailableStock(@Param("id") Long id, @Param("availableStock") int availableStock);

}
