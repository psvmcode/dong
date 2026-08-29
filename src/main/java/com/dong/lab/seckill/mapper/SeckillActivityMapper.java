package com.dong.lab.seckill.mapper;

import com.dong.lab.seckill.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillActivityMapper {

    SeckillActivity selectById(@Param("id") Long id);

    List<SeckillActivity> selectAll();

    int insert(SeckillActivity activity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") int status,
                     @Param("expectedVersion") int expectedVersion);

    int updateAvailableStock(@Param("id") Long id, @Param("availableStock") int availableStock);

}
