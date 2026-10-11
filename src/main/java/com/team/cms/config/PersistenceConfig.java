package com.team.cms.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.team.cms.common.persistence.AuditFieldHandler;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration
@PropertySource(value = {"classpath:jdbc.properties", "file:${cms.config.file:./src/main/resources/jdbc-local.properties}"}, ignoreResourceNotFound = true, encoding = "UTF-8")
@MapperScan(basePackages = "com.team.cms", annotationClass = Mapper.class)
public class PersistenceConfig {
    @Bean(destroyMethod = "close")
    public DataSource dataSource(Environment env) {
        String url = env.getRequiredProperty("jdbc.url");
        String username = env.getRequiredProperty("jdbc.username");
        String password = env.getRequiredProperty("jdbc.password");
        if (url.startsWith("jdbc:mysql:") && (username.isBlank() || password.isBlank())) {
            throw new IllegalStateException("请设置 CMS_DB_USERNAME/CMS_DB_PASSWORD，或创建被 Git 忽略的 jdbc-local.properties");
        }
        DruidDataSource source = new DruidDataSource();
        source.setDriverClassName(env.getRequiredProperty("jdbc.driver"));
        source.setUrl(url); source.setUsername(username); source.setPassword(password);
        source.setInitialSize(0); source.setMinIdle(0); source.setMaxActive(10);
        source.setMaxWait(5000); source.setValidationQuery("SELECT 1");
        source.setTestWhileIdle(true);
        return source;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor plugins = new MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        plugins.addInnerInterceptor(pagination);
        return plugins;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource source, MybatisPlusInterceptor plugins) throws Exception {
        // MP's factory injects BaseMapper statements; the plain MyBatis factory does not.
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        factory.setPlugins(plugins);
        GlobalConfig global = new GlobalConfig();
        global.setBanner(false);
        global.setMetaObjectHandler(new AuditFieldHandler());
        factory.setGlobalConfig(global);
        return factory.getObject();
    }

    @Bean public DataSourceTransactionManager transactionManager(DataSource source) {
        return new DataSourceTransactionManager(source);
    }
}
