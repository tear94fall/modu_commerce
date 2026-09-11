package com.example.commerce.application.config

import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration
@EnableJpaRepositories(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [RoRepository::class],
        ),
    ],
    basePackages = ["com.example.commerce.application.domain.repository.ro"],
    entityManagerFactoryRef = "roEntityManagerFactory",
    transactionManagerRef = "roTransactionManager",
)
class RoJpaConfig {
    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    fun roDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    /** `spring.datasource.replica.hikari.*` 가 풀 설정에 바인딩되는 실제 HikariDataSource */
    @Bean(name = ["roHikariDataSource"])
    @ConfigurationProperties("spring.datasource.replica.hikari")
    fun roHikariDataSource(
        @Qualifier("roDataSourceProperties") props: DataSourceProperties,
    ): HikariDataSource =
        props
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Bean(name = ["roDataSource"])
    fun roDataSource(
        @Qualifier("roHikariDataSource") hikari: HikariDataSource,
    ): DataSource = LazyConnectionDataSourceProxy(hikari)

    /**
     * 레플리카는 읽기 전용이므로 스키마 DDL(hbm2ddl)을 절대 실행하지 않는다.
     * 스키마는 마스터에서 생성되어 복제로 전파된다.
     */
    @Bean(name = ["roEntityManagerFactory"])
    fun roEntityManagerFactory(
        @Qualifier("roDataSource") roDataSource: DataSource,
        builder: EntityManagerFactoryBuilder,
    ): LocalContainerEntityManagerFactoryBean =
        builder
            .dataSource(roDataSource)
            .packages(RwJpaConfig.ENTITY_PACKAGE)
            .persistenceUnit("RO")
            .properties(mapOf("hibernate.hbm2ddl.auto" to "none"))
            .build()

    @Bean(name = ["roTransactionManager"])
    fun roTransactionManager(
        @Qualifier("roEntityManagerFactory") factory: EntityManagerFactory,
    ): PlatformTransactionManager = JpaTransactionManager(factory)
}
