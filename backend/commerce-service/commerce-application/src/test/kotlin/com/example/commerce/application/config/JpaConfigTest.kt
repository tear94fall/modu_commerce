package com.example.commerce.application.config

import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean

@SpringBootTest
class JpaConfigTest
    @Autowired
    constructor(
        @Qualifier("rwHikariDataSource") private val rwHikari: HikariDataSource,
        @Qualifier("roHikariDataSource") private val roHikari: HikariDataSource,
        @Qualifier("&roEntityManagerFactory") private val roEmf: LocalContainerEntityManagerFactoryBean,
        @Qualifier("&rwEntityManagerFactory") private val rwEmf: LocalContainerEntityManagerFactoryBean,
    ) {
        @Test
        fun `hikari 설정이 데이터소스에 바인딩된다`() {
            assertEquals("test-rw-pool", rwHikari.poolName)
            assertEquals(3, rwHikari.maximumPoolSize)
            assertEquals("test-ro-pool", roHikari.poolName)
        }

        @Test
        fun `RO EntityManagerFactory는 DDL을 실행하지 않는다`() {
            assertEquals("none", roEmf.jpaPropertyMap["hibernate.hbm2ddl.auto"])
            assertEquals("update", rwEmf.jpaPropertyMap["hibernate.hbm2ddl.auto"])
        }
    }
