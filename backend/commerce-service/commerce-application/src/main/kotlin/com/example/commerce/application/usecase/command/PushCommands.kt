package com.example.commerce.application.usecase.command

import com.example.commerce.application.domain.entity.PushTargetType
import java.time.OffsetDateTime

/** 캠페인 내용(만들기·테스트 보내기 공통). 값은 앞뒤 공백을 걷은 뒤 검사한다. */
data class PushContentCommand(
    val title: String,
    val body: String,
    val imageUrl: String?,
    val targetType: PushTargetType,
    val targetId: Long?,
) {
    fun validate() {
        require(title.isNotBlank() && title.length <= 40) { "제목은 1~40자로 입력하세요." }
        require(body.isNotBlank() && body.length <= 120) { "내용은 1~120자로 입력하세요." }
        require(imageUrl == null || (imageUrl.length <= 500 && URL.matches(imageUrl))) { "이미지 주소는 http(s) 주소로 입력하세요." }
        if (targetType == PushTargetType.PRODUCT || targetType == PushTargetType.PROMOTION) {
            require(targetId != null) { "보낼 대상을 고르세요." }
        }
    }

    companion object {
        private val URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
    }
}

/** 캠페인 만들기. [scheduledAt] 이 null 이면 지금 보낸다. */
data class PushCampaignCommand(
    val content: PushContentCommand,
    val scheduledAt: OffsetDateTime?,
    val createdBy: String,
)
