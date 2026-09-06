package com.dong.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        basePackages = {
                "com.dong.cache.mapper",
                "com.dong.classic.mapper",
                "com.dong.seckill.mapper",
                "com.dong.redpacket.mapper",
                "com.dong.social.mapper",
                "com.dong.tcc.mapper",
                "com.dong.mq.mapper",
                "com.dong.crossborder.mapper",
                "com.dong.order.mapper"
        },
        sqlSessionFactoryRef = "sqlSessionFactory"
)
/**
 * 主数据源 MyBatis Mapper 扫描配置类。
 */
public class PrimaryMybatisConfig {

}
