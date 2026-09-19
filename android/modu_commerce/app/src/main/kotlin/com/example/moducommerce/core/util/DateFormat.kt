package com.example.moducommerce.core.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 서버 ISO(`2026-09-19T05:00:00`) → `2026.09.19 14:00`. 서버는 UTC 라 기기 시간대로 옮긴다. */
fun formatDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        LocalDateTime.parse(iso.substringBefore('.'))
            .atZone(java.time.ZoneOffset.UTC)
            .withZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"))
    }.getOrDefault(iso)
}
