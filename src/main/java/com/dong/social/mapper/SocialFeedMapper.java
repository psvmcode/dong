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
     * 按 Authors 查询记录。
     */
    List<SocialFeed> selectByAuthors(@Param("authorIds") List<Long> authorIds,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    /**
     * 按 Author 查询记录。
     */
    List<SocialFeed> selectByAuthor(@Param("authorId") Long authorId);

    /**
     * 增加动态点赞数。
     */
    int increaseLikeCount(@Param("feedId") Long feedId);

}
