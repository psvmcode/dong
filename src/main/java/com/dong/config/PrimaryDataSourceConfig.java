package com.dong.config;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
/**
 * 主数据源与 MyBatis 配置类。
 */
@Configuration

public class PrimaryDataSourceConfig {

    public static final String SESSION_FACTORY = "sqlSessionFactory";

    public static final String TRANSACTION_MANAGER = "primaryTransactionManager";

    /**
     * 创建主数据源。
     *
     * @param properties 数据源属性
     * @return Hikari 数据源
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource primaryDataSource(org.springframework.boot.autoconfigure.jdbc.DataSourceProperties properties) {
        return DataSourceBuilder.create(properties.getClassLoader())
                .type(HikariDataSource.class)
                .driverClassName(properties.determineDriverClassName())
                .url(properties.determineUrl())
                .username(properties.determineUsername())
                .password(properties.determinePassword())
                .build();
    }

    /**
     * 创建 SqlSessionFactory。
     *
     * @param primaryDataSource 主数据源
     * @return SqlSessionFactory
     * @throws Exception 创建异常
     */
    @Bean(SESSION_FACTORY)
    @Primary
    public SqlSessionFactory sqlSessionFactory(DataSource primaryDataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(primaryDataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
        factoryBean.setTypeAliasesPackage("com.dong");
        factoryBean.setTypeHandlersPackage("com.dong");
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultFetchSize(100);
        configuration.setLogImpl(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
    }

    /**
     * 创建主事务管理器。
     *
     * @param primaryDataSource 主数据源
     * @return 数据源事务管理器
     */
    @Bean(TRANSACTION_MANAGER)
    @Primary
    public DataSourceTransactionManager primaryTransactionManager(DataSource primaryDataSource) {
        return new DataSourceTransactionManager(primaryDataSource);
    }

}
