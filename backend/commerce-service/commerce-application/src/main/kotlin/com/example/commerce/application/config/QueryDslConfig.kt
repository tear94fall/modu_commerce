package com.example.commerce.application.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.orm.jpa.SharedEntityManagerCreator

@Configuration
class QueryDslConfig {
    @Primary
    @Bean
    fun rwQueryFactory(
        @Qualifier("rwEntityManagerFactory") emf: EntityManagerFactory,
    ): JPAQueryFactory = JPAQueryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))

    @Bean
    fun roQueryFactory(
        @Qualifier("roEntityManagerFactory") emf: EntityManagerFactory,
    ): JPAQueryFactory = JPAQueryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
}
