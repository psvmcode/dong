package com.dong.lab.classic.mapper;

import com.dong.lab.classic.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ShortLinkMapper {

    ShortLink selectByCode(@Param("code") String code);

    int insert(ShortLink shortLink);

    int increaseHitCount(@Param("code") String code);

}
