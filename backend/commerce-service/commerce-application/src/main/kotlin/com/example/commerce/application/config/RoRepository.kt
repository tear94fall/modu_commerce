package com.example.commerce.application.config

import org.springframework.data.repository.NoRepositoryBean
import org.springframework.data.repository.Repository

/** 읽기 전용(replica) 저장소 마커. 이 인터페이스 자체는 빈으로 등록하지 않는다. */
@NoRepositoryBean
interface RoRepository<T, ID> : Repository<T, ID>
