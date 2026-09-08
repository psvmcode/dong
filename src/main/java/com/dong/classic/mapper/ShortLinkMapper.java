package com.dong.classic.mapper;

import com.dong.classic.entity.ShortLink;
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
     * 查询全部短链，定时任务回写点击量时遍历使用。
     */
    java.util.List<ShortLink> selectAll(@Param("limit") int limit);

    /**
     * 插入记录，返回影响行数。
     */
    int insert(ShortLink shortLink);

    /**
     * 回写点击次数。写入的是 Redis 累加器的绝对值而不是增量，
     * 这样即使中途丢过一次回写，下一轮也能自动补平。
     */
    int updateHitCount(@Param("code") String code, @Param("hitCount") long hitCount);

    /**
     * 启停短链并设置过期时间。
     */
    int updateStatus(@Param("code") String code, @Param("enabled") Integer enabled,
                     @Param("expireTime") java.time.LocalDateTime expireTime);

}
