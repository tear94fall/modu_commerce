package com.example.commerce.api.push

import com.example.commerce.api.common.CustomerRequired
import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.userId
import com.example.commerce.application.domain.entity.PushCampaignStatus
import com.example.commerce.application.domain.entity.PushPlatform
import com.example.commerce.application.domain.entity.PushTargetType
import com.example.commerce.application.usecase.command.PushCampaignCommand
import com.example.commerce.application.usecase.command.PushContentCommand
import com.example.commerce.application.usecase.push.AdminPushCampaignUseCase
import com.example.commerce.application.usecase.push.MarkPushOpenedUseCase
import com.example.commerce.application.usecase.push.PushConsentUseCase
import com.example.commerce.application.usecase.push.PushDeviceUseCase
import com.example.commerce.application.usecase.push.PushInboxUseCase
import com.example.commerce.application.usecase.result.AdminPushCampaignResult
import com.example.commerce.application.usecase.result.NotificationItemResult
import com.example.commerce.application.usecase.result.PushAudienceResult
import com.example.commerce.application.usecase.result.PushConsentResult
import com.example.commerce.application.usecase.result.PushTestResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** 앱: 푸시 토큰, 광고성 정보 수신 동의, 알림 열어 봄. */
@Tag(
    name = "푸시·알림 설정 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). 모두 계정 토큰(aud modu-commerce) 필요. 기기 등록·동의 변경은 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED. 기기 해제·동의 조회·열어 봄 기록은 가입 전에도 부를 수 있다.",
)
@RestController
@RequestMapping("/api/v1")
class PushController(
    private val pushDeviceUseCase: PushDeviceUseCase,
    private val pushConsentUseCase: PushConsentUseCase,
    private val markPushOpenedUseCase: MarkPushOpenedUseCase,
) {
    @Operation(
        summary = "푸시 기기 등록",
        description =
            "앱의 FCM 토큰을 등록하거나 마지막 확인 시각을 고치고 204. 다른 사람에게 등록된 토큰이면 이 사람에게 옮긴다(멱등). " +
                "토큰이 비었거나 512자를 넘으면 400. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PutMapping("/me/push/devices")
    fun registerDevice(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PushDeviceRequest,
    ): ResponseEntity<Void> {
        val token = requireNotNull(request.token?.takeIf { it.isNotBlank() }) { "푸시 토큰이 없습니다." }
        pushDeviceUseCase.register(jwt.userId(), token, request.platform ?: PushPlatform.ANDROID)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "푸시 기기 해제", description = "로그아웃 때 부른다. 내 토큰이면 지우고, 없거나 남의 토큰이면 아무것도 하지 않는다. 항상 204.")
    @DeleteMapping("/me/push/devices")
    fun removeDevice(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "지울 FCM 토큰(앞뒤 공백 무시)", example = "fcm-token-abc123")
        @RequestParam token: String,
    ): ResponseEntity<Void> {
        pushDeviceUseCase.remove(jwt.userId(), token)
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "혜택·이벤트 알림 동의 조회",
        description = "광고성 푸시(혜택·이벤트 알림)와 야간(21~08시) 수신 동의 여부·바꾼 시각을 돌려준다. 기록이 없으면 둘 다 false.",
    )
    @GetMapping("/me/push/consent")
    fun consent(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PushConsentResult> = ResponseEntity.ok(pushConsentUseCase.get(jwt.userId()))

    @Operation(
        summary = "혜택·이벤트 알림 동의 변경",
        description =
            "두 동의 값을 함께 바꾸고 바뀐 값의 시각만 새로 적는다. 혜택 알림을 끄면 야간 알림도 꺼진다. 둘 중 하나라도 빠지면 400. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
    )
    @CustomerRequired
    @PutMapping("/me/push/consent")
    fun changeConsent(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PushConsentRequest,
    ): ResponseEntity<PushConsentResult> {
        val marketing = requireNotNull(request.marketing) { "혜택·이벤트 알림 동의 여부가 없습니다." }
        val night = requireNotNull(request.night) { "야간 알림 동의 여부가 없습니다." }
        return ResponseEntity.ok(pushConsentUseCase.change(jwt.userId(), marketing, night))
    }

    @Operation(
        summary = "푸시 열어 봄 기록",
        description = "푸시 알림을 눌러 앱을 열었을 때 부른다. 캠페인 열람 수를 사람마다 한 번만 세고 알림함의 그 줄을 읽음으로 바꾼다. 없는 캠페인이어도 204.",
    )
    @PostMapping("/push/campaigns/{id}/opened")
    fun opened(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "푸시 캠페인 id(알림 데이터의 campaignId)", example = "8")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        markPushOpenedUseCase.execute(id, jwt.userId())
        return ResponseEntity.noContent().build()
    }
}

/** 앱: 알림함. 받은 지 30일 안쪽의 캠페인 알림, 최신 순. */
@Tag(
    name = "알림함 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). " +
            "모두 계정 토큰(aud modu-commerce) 필요. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
@CustomerRequired
@RestController
@RequestMapping("/api/v1/me/notifications")
class NotificationController(
    private val pushInboxUseCase: PushInboxUseCase,
) {
    @Operation(summary = "알림함 조회", description = "받은 지 30일 안쪽의 캠페인 알림을 최신 순으로 한 페이지 돌려준다(읽음 여부 포함).")
    @GetMapping
    fun notifications(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~50 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<NotificationItemResult>> =
        ResponseEntity.ok(PageResponse.from(pushInboxUseCase.page(jwt.userId(), page, size)) { it })

    @Operation(summary = "안 읽은 알림 수 조회", description = "30일 안쪽의 안 읽은 알림 수를 {unread: 개수} 로 돌려준다(벨 배지용).")
    @GetMapping("/unread-count")
    fun unreadCount(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Map<String, Long>> = ResponseEntity.ok(mapOf("unread" to pushInboxUseCase.unread(jwt.userId())))

    @Operation(summary = "알림 읽음 처리", description = "알림함의 내 알림 하나를 읽음으로 바꾸고 204. 내 알림이 아니거나 없으면 404.")
    @PostMapping("/{id}/read")
    fun read(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "알림함 줄 id", example = "30")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        pushInboxUseCase.markRead(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "알림 모두 읽음 처리", description = "내 안 읽은 알림을 모두 읽음으로 바꾸고 204.")
    @PostMapping("/read-all")
    fun readAll(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        pushInboxUseCase.markAllRead(jwt.userId())
        return ResponseEntity.noContent().build()
    }
}

/** 백오피스: 푸시 캠페인(광고 문구·야간 규칙·동의 확인은 서버가 한다). */
@Tag(
    name = "푸시 캠페인 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/push-campaigns")
class AdminPushCampaignController(
    private val adminPushCampaignUseCase: AdminPushCampaignUseCase,
) {
    @Operation(
        summary = "캠페인 목록 검색",
        description = "보낼(보낸) 시각 최신 순으로 한 페이지 돌려준다. 발송 결과(대상·성공·실패 수)와 열어 본 수가 붙는다.",
    )
    @GetMapping
    fun campaigns(
        @Parameter(
            description = "상태: SCHEDULED(예약), SENDING(보내는 중), SENT(보냄), CANCELED(취소), FAILED(실패). 없으면 전체",
            example = "SCHEDULED",
        )
        @RequestParam(required = false) status: PushCampaignStatus?,
        @Parameter(description = "제목 부분 일치(대소문자 무시)", example = "가을")
        @RequestParam(required = false) q: String?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 15, 1~100 으로 자른다", example = "15")
        @RequestParam(defaultValue = "15") size: Int,
    ): ResponseEntity<PageResponse<AdminPushCampaignResult>> =
        ResponseEntity.ok(PageResponse.from(adminPushCampaignUseCase.search(status, q, page, size)) { it })

    @Operation(
        summary = "받을 사람 수 미리 보기",
        description =
            "그 시각에 보내면 받을 회원·기기 수를 돌려준다. 21:00~08:00(KST)는 야간이라 야간 동의까지 한 회원만 센다. " +
                "혜택·이벤트 알림 동의 수와 야간 동의 수도 함께 온다. 시각 형식이 틀리면 400.",
    )
    @GetMapping("/audience")
    fun audience(
        @Parameter(
            description = "보낼 시각(오프셋 있는 ISO-8601). 없으면 지금. '+' 를 인코딩하지 않아 공백으로 와도 읽는다",
            example = "2026-09-27T21:30:00+09:00",
        )
        @RequestParam(required = false) at: String?,
    ): ResponseEntity<PushAudienceResult> =
        ResponseEntity.ok(adminPushCampaignUseCase.audience(at?.takeIf { it.isNotBlank() }?.let { parseOffset(it).toInstant() }))

    @Operation(summary = "캠페인 상세 조회", description = "내용·대상·예약 시각·발송 결과를 돌려준다. 없는 캠페인이면 404.")
    @GetMapping("/{id}")
    fun campaign(
        @Parameter(description = "캠페인 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<AdminPushCampaignResult> = ResponseEntity.ok(adminPushCampaignUseCase.get(id))

    @Operation(
        summary = "캠페인 만들기(예약·즉시 발송)",
        description =
            "201 과 캠페인을 돌려준다. scheduledAt 이 없으면 커밋 뒤 바로 보내고, 있으면 그 시각에 스케줄러가 보낸다(30일 이내). " +
                "제목 앞 '(광고)', 본문 끝 수신거부 안내는 서버가 붙이고, 동의한 회원(야간이면 야간 동의까지)에게만 간다. " +
                "문구 길이·대상·예약 시각이 틀리면 400. 만든 직원은 토큰 sub 로 남는다.",
    )
    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: PushCampaignRequest,
    ): ResponseEntity<AdminPushCampaignResult> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            adminPushCampaignUseCase.create(PushCampaignCommand(request.toContent(), request.scheduledAt, jwt.subject)),
        )

    @Operation(
        summary = "캠페인 예약 취소",
        description = "예약(SCHEDULED) 상태인 캠페인만 취소하고 바뀐 캠페인을 돌려준다. 이미 보내기 시작했거나 끝났으면 409, 없으면 404.",
    )
    @PostMapping("/{id}/cancel")
    fun cancel(
        @Parameter(description = "캠페인 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<AdminPushCampaignResult> = ResponseEntity.ok(adminPushCampaignUseCase.cancel(id))

    @Operation(
        summary = "테스트 푸시 보내기",
        description =
            "고른 회원(1~5명)의 기기로 동의·야간 규칙과 상관없이 바로 보낸다. 제목 앞에 '[테스트]' 가 붙고 캠페인으로 남지 않는다. " +
                "보낸 기기·성공·실패 수와 기기가 없는 회원을 돌려준다. 입력이 틀리면 400, FCM 호출이 실패하면 503.",
    )
    @PostMapping("/test")
    fun test(
        @RequestBody request: PushTestRequest,
    ): ResponseEntity<PushTestResult> =
        ResponseEntity.ok(
            adminPushCampaignUseCase.test(
                toContent(request.title, request.body, request.imageUrl, request.targetType, request.targetId),
                request.userIds.orEmpty(),
            ),
        )

    companion object {
        /** 쿼리 문자열의 "+09:00" 은 인코딩하지 않으면 공백으로 온다. 되돌려서 읽는다. */
        fun parseOffset(value: String): OffsetDateTime =
            try {
                OffsetDateTime.parse(value.trim().replace(' ', '+'))
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("시각은 2026-09-27T21:30:00+09:00 형식으로 보내세요.")
            }
    }
}

@Schema(description = "푸시 기기 등록 요청")
data class PushDeviceRequest(
    @field:Schema(description = "FCM 등록 토큰. 필수, 512자 이하(앞뒤 공백 무시)", example = "fcm-token-abc123")
    val token: String? = null,
    @field:Schema(description = "기기 종류. 선택, 기본 ANDROID(지금은 ANDROID 만)", example = "ANDROID")
    val platform: PushPlatform? = null,
)

@Schema(description = "혜택·이벤트 알림 동의 변경 요청. 두 값을 모두 보낸다")
data class PushConsentRequest(
    @field:Schema(description = "혜택·이벤트 알림(광고성 푸시) 수신 동의. 필수", example = "true")
    val marketing: Boolean? = null,
    @field:Schema(description = "야간(21~08시) 광고성 푸시 수신 동의. 필수. marketing=false 면 무시하고 false 로 둔다", example = "false")
    val night: Boolean? = null,
)

/** 캠페인 만들기. [scheduledAt] 은 오프셋이 있는 ISO-8601("2026-09-27T21:30:00+09:00"), null 이면 지금 보낸다. */
@Schema(description = "푸시 캠페인 만들기 본문. '(광고)' 표시와 수신거부 안내는 서버가 붙이니 넣지 않는다.")
data class PushCampaignRequest(
    @field:Schema(description = "제목. 1~40자('(광고)' 제외)", example = "가을 문구 기획전 시작")
    val title: String? = null,
    @field:Schema(description = "내용. 1~120자", example = "필기구 최대 30% 할인, 지금 확인하세요.")
    val body: String? = null,
    @field:Schema(description = "큰 이미지 주소 http(s)://… 500자 이하. 선택", example = "https://example.com/push/fall.jpg")
    val imageUrl: String? = null,
    @field:Schema(
        description = "누르면 갈 화면: PRODUCT(상품), PROMOTION(기획전·이벤트), COUPONS(쿠폰함), HOME. 필수",
        example = "PROMOTION",
    )
    val targetType: PushTargetType? = null,
    @field:Schema(
        description = "PRODUCT·PROMOTION 일 때 대상 id. 판매 중인 상품·노출 중인 기획전만 된다. 다른 종류면 무시",
        example = "1",
    )
    val targetId: Long? = null,
    @field:Schema(
        description = "예약 시각(오프셋 있는 ISO-8601). 없으면 지금 보낸다. 지난 시각(1분 여유)·30일 넘게 뒤면 400",
        example = "2026-09-27T21:30:00+09:00",
    )
    val scheduledAt: OffsetDateTime? = null,
) {
    fun toContent(): PushContentCommand = toContent(title, body, imageUrl, targetType, targetId)
}

@Schema(description = "테스트 푸시 본문. 내용 규칙은 캠페인과 같다.")
data class PushTestRequest(
    @field:Schema(description = "제목. 1~40자", example = "가을 문구 기획전 시작")
    val title: String? = null,
    @field:Schema(description = "내용. 1~120자", example = "필기구 최대 30% 할인, 지금 확인하세요.")
    val body: String? = null,
    @field:Schema(description = "큰 이미지 주소 http(s)://… 500자 이하. 선택", example = "https://example.com/push/fall.jpg")
    val imageUrl: String? = null,
    @field:Schema(description = "누르면 갈 화면: PRODUCT, PROMOTION, COUPONS, HOME. 필수", example = "HOME")
    val targetType: PushTargetType? = null,
    @field:Schema(description = "PRODUCT·PROMOTION 일 때 대상 id", example = "1")
    val targetId: Long? = null,
    @field:Schema(description = "받을 회원 id 1~5명(동의와 상관없이 그 회원의 모든 기기로)", example = "[\"42\"]")
    val userIds: List<String>? = null,
)

private fun toContent(
    title: String?,
    body: String?,
    imageUrl: String?,
    targetType: PushTargetType?,
    targetId: Long?,
) = PushContentCommand(
    title = title?.trim().orEmpty(),
    body = body?.trim().orEmpty(),
    imageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() },
    targetType = requireNotNull(targetType) { "보낼 대상 종류를 고르세요." },
    targetId = targetId,
)
