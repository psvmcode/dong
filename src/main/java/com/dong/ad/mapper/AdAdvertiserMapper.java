package com.dong.ad.mapper;

import com.dong.ad.entity.AdAdvertiser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 广告主账户数据访问接口。
 */
@Mapper
public interface AdAdvertiserMapper {

    /**
     * 根据 id 查询账户。
     *
     * @param id 主键
     * @return 账户
     */
    AdAdvertiser selectById(@Param("id") Long id);

    /**
     * 根据编号查询账户。
     *
     * @param advertiserNo 广告主编号
     * @return 账户
     */
    AdAdvertiser selectByNo(@Param("advertiserNo") String advertiserNo);

    /**
     * 分页查询账户。
     *
     * @param status  状态过滤，null 表示不过滤
     * @param keyword 名称或编号关键字
     * @param offset  偏移量
     * @param size    每页大小
     * @return 账户列表
     */
    List<AdAdvertiser> selectByPage(@Param("status") Integer status, @Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);

    /**
     * 统计分页查询总数。
     *
     * @param status  状态过滤
     * @param keyword 名称或编号关键字
     * @return 总数
     */
    long countByPage(@Param("status") Integer status, @Param("keyword") String keyword);

    /**
     * 新增账户。
     *
     * @param advertiser 账户实体
     * @return 影响行数
     */
    int insert(AdAdvertiser advertiser);

    /**
     * 充值，条件里带状态，冻结账户不接受充值。
     *
     * @param id          账户 id
     * @param amountCents 充值金额，单位分
     * @param status      期望状态
     * @return 影响行数
     */
    int addBalance(@Param("id") Long id, @Param("amountCents") long amountCents, @Param("status") int status);

    /**
     * 条件扣减余额。余额充足与账户正常都写进 where，
     * 靠影响行数判定成败，避免先查后扣在并发下扣成负数。
     *
     * @param id          账户 id
     * @param amountCents 扣减金额，单位分
     * @param status      期望状态
     * @return 影响行数
     */
    int deductBalance(@Param("id") Long id, @Param("amountCents") long amountCents, @Param("status") int status);

    /**
     * 条件更新账户日限额，带版本号防并发覆盖。
     *
     * @param id              账户 id
     * @param dailyLimitCents 新的日限额，单位分，0 表示不限
     * @param expectedVersion 期望版本号
     * @return 影响行数
     */
    int updateDailyLimit(@Param("id") Long id, @Param("dailyLimitCents") long dailyLimitCents, @Param("expectedVersion") int expectedVersion);

}
