package com.dong.lab.replica.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "lab.mariadb", name = "enabled", havingValue = "true")
@MapperScan(basePackages = "com.dong.lab.replica.mapper",
        sqlSessionFactoryRef = "replicaSqlSessionFactory")
public class MariaDbConfig {

    @Value("${lab.mariadb.url}")
    private String url;

    @Value("${lab.mariadb.username}")
    private String username;

    @Value("${lab.mariadb.password}")
    private String password;

    @Value("${lab.mariadb.driver-class-name:org.mariadb.jdbc.Driver}")
    private String driverClassName;

    @Bean(name = "replicaDataSource")
    public DataSource replicaDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setPoolName("replica-hikari");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000L);
        return new HikariDataSource(config);
    }

    @Bean(name = "replicaSqlSessionFactory")
    public SqlSessionFactory replicaSqlSessionFactory(@Qualifier("replicaDataSource") DataSource dataSource)
            throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/replica/*.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factoryBean.setConfiguration(configuration);
        return factoryBean.getObject();
    }

    @Bean(name = "replicaTransactionManager")
    public DataSourceTransactionManager replicaTransactionManager(
            @Qualifier("replicaDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

}
