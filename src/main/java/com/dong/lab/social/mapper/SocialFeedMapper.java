package com.dong.lab.social.mapper;

import com.dong.lab.social.entity.SocialFeed;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SocialFeedMapper {

    int insert(SocialFeed feed);

    SocialFeed selectByFeedId(@Param("feedId") Long feedId);

    List<SocialFeed> selectByAuthors(@Param("authorIds") List<Long> authorIds,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    List<SocialFeed> selectByAuthor(@Param("authorId") Long authorId);

    int increaseLikeCount(@Param("feedId") Long feedId);

}
