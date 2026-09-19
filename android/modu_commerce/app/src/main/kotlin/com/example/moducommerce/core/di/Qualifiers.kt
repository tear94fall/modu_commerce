package com.example.moducommerce.core.di

import javax.inject.Qualifier

@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

/** 프로세스가 사는 동안 유지되는 `SupervisorJob` 스코프. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationScope
