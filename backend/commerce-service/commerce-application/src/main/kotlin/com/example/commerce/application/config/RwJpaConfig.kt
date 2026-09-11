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
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.RollbackOn
import javax.sql.DataSource

@Configuration
@EnableJpaRepositories(
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [RwRepository::class],
        ),
    ],
    basePackages = ["com.example.commerce.application.domain.repository.rw"],
    entityManagerFactoryRef = "rwEntityManagerFactory",
    transactionManagerRef = "rwTransactionManager",
)
@EnableTransactionManagement(rollbackOn = RollbackOn.ALL_EXCEPTIONS)
class RwJpaConfig {
    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.master")
    fun rwDataSourceProperties(): DataSourceProperties = DataSourceProperties()

    /** `spring.datasource.master.hikari.*` 가 풀 설정에 바인딩되는 실제 HikariDataSource */
    @Bean(name = ["rwHikariDataSource"])
    @ConfigurationProperties("spring.datasource.master.hikari")
    fun rwHikariDataSource(
        @Qualifier("rwDataSourceProperties") props: DataSourceProperties,
    ): HikariDataSource =
        props
            .initializeDataSourceBuilder()
            .type(HikariDataSource::class.java)
            .build()

    @Primary
    @Bean(name = ["rwDataSource", "dataSource"])
    fun rwDataSource(
        @Qualifier("rwHikariDataSource") hikari: HikariDataSource,
    ): DataSource = LazyConnectionDataSourceProxy(hikari)

    @Primary
    @Bean(name = ["rwEntityManagerFactory"])
    fun rwEntityManagerFactory(
        @Qualifier("rwDataSource") rwDataSource: DataSource,
        builder: EntityManagerFactoryBuilder,
    ): LocalContainerEntityManagerFactoryBean =
        builder
            .dataSource(rwDataSource)
            .packages(ENTITY_PACKAGE)
            .persistenceUnit("RW")
            .build()

    @Primary
    @Bean(name = ["rwTransactionManager"])
    fun rwTransactionManager(
        @Qualifier("rwEntityManagerFactory") factory: EntityManagerFactory,
    ): PlatformTransactionManager = JpaTransactionManager(factory)

    companion object {
        const val ENTITY_PACKAGE = "com.example.commerce.application.domain.entity"
    }
}
