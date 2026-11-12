package com.dong.social.mapper;

import com.dong.social.entity.SocialRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SocialRelationMapper，MyBatis 数据访问接口。
 */
@Mapper
public interface SocialRelationMapper {

    /**
     * 插入记录，返回影响行数。
     */
    int insert(SocialRelation relation);

    /**
     * 删除关注关系。
     */
    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * 判断是否存在关注关系。
     */
    int countByFollowerAndFollowee(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /**
     * 查询关注列表，SQL 层封顶，大 V 的关注数不能一次性装进 JVM。
     *
     * @param followerId 关注者 id
     * @param limit       最大条数
     * @return 关注列表
     */
    List<Long> selectFollowees(@Param("followerId") Long followerId, @Param("limit") int limit);

    /**
     * 查询粉丝列表，SQL 层封顶，避免发布动态时把全量粉丝读进内存。
     *
     * @param followeeId 被关注者 id
     * @param limit       最大条数
     * @return 粉丝列表
     */
    List<Long> selectFollowers(@Param("followeeId") Long followeeId, @Param("limit") int limit);

    /**
     * 统计关注数。
     */
    long countFollowees(@Param("followerId") Long followerId);

    /**
     * 统计粉丝数。
     */
    long countFollowers(@Param("followeeId") Long followeeId);

}
