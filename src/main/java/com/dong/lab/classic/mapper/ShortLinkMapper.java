package com.dong.lab.classic.mapper;

import com.dong.lab.classic.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/**
 * 短链接数据访问接口。
 */
@Mapper

public interface ShortLinkMapper {

    /**
     * 按 Code 查询记录。
     */
    ShortLink selectByCode(@Param("code") String code);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(ShortLink shortLink);

    /**
     * 增加短链点击次数。
     */
    int increaseHitCount(@Param("code") String code);

}
