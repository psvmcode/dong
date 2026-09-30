package com.dong.social.mapper;

import com.dong.social.entity.SocialFeed;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SocialFeedMapper，MyBatis 数据访问接口。
 */
@Mapper
public interface SocialFeedMapper {

    /**
     * 插入记录，返回影响行数。
     */
    int insert(SocialFeed feed);

    /**
     * 按 FeedId 查询记录。
     */
    SocialFeed selectByFeedId(@Param("feedId") Long feedId);

    /**
     * 按动态 id 列表批量查询。推模式时间线一次要取几十条动态，
     * 逐条回表会把一次读放大成几十次查询。
     */
    List<SocialFeed> selectByFeedIds(@Param("feedIds") List<Long> feedIds);

    /**
     * 按 Authors 查询记录。
     */
    List<SocialFeed> selectByAuthors(@Param("authorIds") List<Long> authorIds, @Param("offset") int offset, @Param("size") int size);

    /**
     * 按 Author 查询记录。
     */
    List<SocialFeed> selectByAuthor(@Param("authorId") Long authorId);

    /**
     * 增加动态点赞数。
     */
    int increaseLikeCount(@Param("feedId") Long feedId);

}
