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
     * 查询关注列表。
     */
    List<Long> selectFollowees(@Param("followerId") Long followerId);

    /**
     * 查询粉丝列表。
     */
    List<Long> selectFollowers(@Param("followeeId") Long followeeId);

    /**
     * 统计关注数。
     */
    long countFollowees(@Param("followerId") Long followerId);

    /**
     * 统计粉丝数。
     */
    long countFollowers(@Param("followeeId") Long followeeId);

}
