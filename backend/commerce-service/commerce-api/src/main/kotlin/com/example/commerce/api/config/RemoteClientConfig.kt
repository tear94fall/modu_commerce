package com.example.commerce.api.config

import com.example.commerce.api.member.RestClientMemberLookup
import com.example.commerce.api.point.PointClient
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 다른 서비스(point-service, member-service)를 부르는 클라이언트. Boot 가 주는 RestClient.Builder(요청 id 전달 인터셉터 포함)에
 * 서비스별 연결·응답 시간 한도를 건 요청 팩토리를 끼운다. 한도가 없으면 상대가 멈췄을 때 요청 스레드가 끝없이 기다린다.
 */
@Configuration
class RemoteClientConfig {
    @Bean
    fun pointClient(
        builder: RestClient.Builder,
        props: ModuPointProperties,
        @Value("\${modu.internal-api.token}") internalToken: String,
        circuitBreakerRegistry: CircuitBreakerRegistry,
    ): PointClient =
        PointClient(
            builder.requestFactory(requestFactory(props.connectTimeout, props.readTimeout)),
            props,
            internalToken,
            circuitBreakerRegistry,
        )

    @Bean
    fun restClientMemberLookup(
        builder: RestClient.Builder,
        props: ModuMemberProperties,
        @Value("\${modu.internal-api.token}") internalToken: String,
    ): RestClientMemberLookup =
        RestClientMemberLookup(builder.requestFactory(requestFactory(props.connectTimeout, props.readTimeout)), props, internalToken)

    companion object {
        /** Boot 가 고르는 HTTP 클라이언트(클래스패스 기준)에 연결·응답 한도만 건다. */
        fun requestFactory(
            connectTimeout: Duration,
            readTimeout: Duration,
        ): ClientHttpRequestFactory =
            ClientHttpRequestFactoryBuilder.detect().build(
                ClientHttpRequestFactorySettings
                    .defaults()
                    .withConnectTimeout(connectTimeout)
                    .withReadTimeout(readTimeout),
            )
    }
}
