package com.dong.lab.social.mapper;

import com.dong.lab.social.entity.SocialRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SocialRelationMapper {

    int insert(SocialRelation relation);

    int delete(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    int countByFollowerAndFollowee(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    List<Long> selectFollowees(@Param("followerId") Long followerId);

    List<Long> selectFollowers(@Param("followeeId") Long followeeId);

    long countFollowees(@Param("followerId") Long followerId);

    long countFollowers(@Param("followeeId") Long followeeId);

}
