package com.example.commerce.api.config

import com.example.commerce.application.common.logger
import com.example.commerce.application.service.PromotionCaches
import com.example.commerce.application.service.PromotionSnapshot
import com.example.commerce.application.usecase.result.PromotionBannerResult
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CachingConfigurer
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.cache.interceptor.CacheErrorHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import java.time.Duration

/**
 * 캐시 설정. `modu.cache.redis-enabled=true`(config-service 의 commerce-service.yml)면 공용 Redis 클러스터,
 * 아니면(테스트·IDE) 메모리 캐시다. 키는 `commerce:<캐시 이름>::<키>` 라 메신저가 쓰는 키와 섞이지 않는다.
 */
@ConfigurationProperties("modu.cache")
data class ModuCacheProperties(
    val redisEnabled: Boolean = false,
    /** 홈 배너 목록. 백오피스 변경은 바로 비우므로 이 시간은 놓친 변경(예: DB 직접 수정)의 최대 지연이다. */
    val bannersTtl: Duration = Duration.ofMinutes(5),
    val promotionTtl: Duration = Duration.ofMinutes(10),
    val keyPrefix: String = "commerce:",
)

@Configuration
@EnableCaching
class CacheConfig(
    private val props: ModuCacheProperties,
) : CachingConfigurer {
    /** 캐시 비우기는 트랜잭션 밖의 유스케이스(SavePromotionUseCase)가 커밋 뒤에 한다. */
    @Bean
    fun cacheManager(
        connectionFactory: ObjectProvider<RedisConnectionFactory>,
        objectMapper: ObjectMapper,
    ): CacheManager {
        if (!props.redisEnabled) {
            return ConcurrentMapCacheManager(PromotionCaches.BANNERS, PromotionCaches.PROMOTION)
        }
        val mapper = cacheObjectMapper(objectMapper)
        val types = mapper.typeFactory

        fun config(
            ttl: Duration,
            type: JavaType,
        ) = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(ttl)
            .prefixCacheNameWith(props.keyPrefix)
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(Jackson2JsonRedisSerializer<Any>(mapper, type)))
        return RedisCacheManager
            .builder(connectionFactory.getObject())
            .withCacheConfiguration(
                PromotionCaches.BANNERS,
                config(props.bannersTtl, types.constructCollectionType(List::class.java, PromotionBannerResult::class.java)),
            ).withCacheConfiguration(
                PromotionCaches.PROMOTION,
                config(props.promotionTtl, types.constructType(PromotionSnapshot::class.java)),
            ).disableCreateOnMissingCache()
            .enableStatistics()
            .build()
    }

    companion object {
        /** Redis 에 넣는 JSON. 날짜는 "2026-09-25" 문자열(redis-cli 로 봐도 읽힌다). */
        fun cacheObjectMapper(base: ObjectMapper): ObjectMapper = base.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    /** Redis 가 죽어도 조회는 DB 로 계속된다. 캐시 오류는 로그만 남긴다. */
    override fun errorHandler(): CacheErrorHandler = LoggingCacheErrorHandler

    object LoggingCacheErrorHandler : CacheErrorHandler {
        override fun handleCacheGetError(
            exception: RuntimeException,
            cache: Cache,
            key: Any,
        ) = warn("get", cache, key, exception)

        override fun handleCachePutError(
            exception: RuntimeException,
            cache: Cache,
            key: Any,
            value: Any?,
        ) = warn("put", cache, key, exception)

        override fun handleCacheEvictError(
            exception: RuntimeException,
            cache: Cache,
            key: Any,
        ) = warn("evict", cache, key, exception)

        override fun handleCacheClearError(
            exception: RuntimeException,
            cache: Cache,
        ) = warn("clear", cache, "*", exception)

        private fun warn(
            op: String,
            cache: Cache,
            key: Any,
            e: RuntimeException,
        ) {
            logger.warn { "cache $op failed (${cache.name}::$key), falling back to DB: ${e.message}" }
        }
    }
}
