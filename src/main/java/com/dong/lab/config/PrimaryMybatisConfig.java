package com.dong.lab.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(
        basePackages = {
                "com.dong.lab.cache.mapper",
                "com.dong.lab.classic.mapper",
                "com.dong.lab.seckill.mapper",
                "com.dong.lab.redpacket.mapper",
                "com.dong.lab.social.mapper",
                "com.dong.lab.tcc.mapper",
                "com.dong.lab.mq.mapper",
                "com.dong.lab.crossborder.mapper"
        },
        sqlSessionFactoryRef = "sqlSessionFactory"
)
public class PrimaryMybatisConfig {

}
