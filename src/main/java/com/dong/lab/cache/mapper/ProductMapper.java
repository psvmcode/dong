package com.dong.lab.cache.mapper;

import com.dong.lab.cache.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
/**
 * 商品数据访问接口。
 */
@Mapper

public interface ProductMapper {

    /**
     * 根据 id 查询记录。
     *
     * @param id 商品 id
     * @return 商品记录
     */
    Product selectById(@Param("id") Long id);

    /**
     * 查询所有记录。
     *
     * @return 商品列表
     */
    List<Product> selectAll();

    /**
     * 分页查询记录。
     *
     * @param offset 偏移量
     * @param size   每页大小
     * @return 商品列表
     */
    List<Product> selectByPage(@Param("offset") int offset, @Param("size") int size);

    /**
     * 统计所有记录数。
     *
     * @return 记录总数
     */
    long countAll();

    /**
     * 查询所有商品 id。
     *
     * @return 商品 id 列表
     */
    List<Long> selectAllIds();

    /**
     * 插入记录，返回影响行数。
     *
     * @param product 商品记录
     * @return 影响行数
     */
    int insert(Product product);

    /**
     * 更新记录，返回影响行数。
     *
     * @param product 商品记录
     * @return 影响行数
     */
    int update(Product product);

    /**
     * 根据 id 删除记录。
     *
     * @param id 商品 id
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

}
