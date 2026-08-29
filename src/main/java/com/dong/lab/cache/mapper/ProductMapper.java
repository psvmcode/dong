package com.dong.lab.cache.mapper;

import com.dong.lab.cache.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    Product selectById(@Param("id") Long id);

    List<Product> selectAll();

    List<Product> selectByPage(@Param("offset") int offset, @Param("size") int size);

    long countAll();

    List<Long> selectAllIds();

    int insert(Product product);

    int update(Product product);

    int deleteById(@Param("id") Long id);

}
