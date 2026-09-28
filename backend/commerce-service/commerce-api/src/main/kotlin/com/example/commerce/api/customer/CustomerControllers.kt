package com.example.commerce.api.customer

import com.example.commerce.api.common.PageResponse
import com.example.commerce.api.common.customerRequired
import com.example.commerce.api.common.userId
import com.example.commerce.application.service.CustomerRequiredException
import com.example.commerce.application.usecase.command.TierCommand
import com.example.commerce.application.usecase.customer.AdminCustomerUseCase
import com.example.commerce.application.usecase.customer.AdminTierUseCase
import com.example.commerce.application.usecase.customer.MyCustomerUseCase
import com.example.commerce.application.usecase.customer.PublicTiersUseCase
import com.example.commerce.application.usecase.customer.WithdrawCustomerUseCase
import com.example.commerce.application.usecase.result.AdminCustomerDetailResult
import com.example.commerce.application.usecase.result.AdminCustomerResult
import com.example.commerce.application.usecase.result.AdminTierResult
import com.example.commerce.application.usecase.result.CustomerLookupResult
import com.example.commerce.application.usecase.result.CustomerMeResult
import com.example.commerce.application.usecase.result.CustomerSummaryResult
import com.example.commerce.application.usecase.result.PublicTierResult
import com.example.commerce.application.usecase.result.TierRunResult
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
import java.net.URI

@Schema(description = "모두의 커머스 가입(약관 동의) 요청")
data class JoinCustomerRequest(
    @field:Schema(description = "이용약관 동의. 필수, true 여야 한다", example = "true")
    val agreeTerms: Boolean? = null,
    @field:Schema(description = "개인정보 수집·이용 동의. 필수, true 여야 한다", example = "true")
    val agreePrivacy: Boolean? = null,
    /** 혜택·이벤트 알림(선택). true 면 광고성 푸시 동의(야간 제외). */
    @field:Schema(description = "혜택·이벤트 알림 동의. 선택, 기본 false. true 면 광고성 푸시 수신에 동의한다(야간 수신은 끔)", example = "false")
    val marketing: Boolean? = null,
)

/** 앱: 커머스 가입(약관 동의)과 내 등급. */
@Tag(
    name = "커머스 가입 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). " +
            "모두 계정 토큰(aud modu-commerce) 필요. 가입 전에도 부를 수 있다(가입 여부 확인과 가입 자체).",
)
@RestController
@RequestMapping("/api/v1/me/customer")
class MyCustomerController(
    private val myCustomerUseCase: MyCustomerUseCase,
) {
    /** 가입하지 않았거나 탈퇴했거나 동의 전이면 404 `{message, code:"CUSTOMER_REQUIRED"}`. */
    @Operation(
        summary = "내 커머스 가입·등급 조회",
        description =
            "가입(동의) 정보와 이번 달 등급, 최근 누적 구매액으로 본 예상 등급·다음 등급까지 남은 금액을 돌려준다. " +
                "가입하지 않았거나 탈퇴했거나 동의 전(옮겨 온 회원)이면 404 {message, code:\"CUSTOMER_REQUIRED\"} — 앱은 이걸 보고 가입 화면을 띄운다.",
    )
    @GetMapping
    fun me(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(myCustomerUseCase.get(jwt.userId()))
        } catch (e: CustomerRequiredException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(customerRequired())
        }

    @Operation(
        summary = "커머스 가입(약관 동의)",
        description =
            "필수 약관 둘에 동의해 고객이 되고 201 과 내 가입·등급 정보를 돌려준다. 첫 가입이면 지난 구매액으로 등급을 정한다. " +
                "탈퇴했거나 옮겨 온 회원은 데이터를 그대로 두고 다시 활성화한다(여러 번 불러도 된다). " +
                "marketing=true 면 혜택·이벤트 알림에도 동의한다. 필수 약관 중 하나라도 빠지면 400.",
    )
    @PostMapping
    fun join(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody request: JoinCustomerRequest,
    ): ResponseEntity<CustomerMeResult> {
        val me =
            myCustomerUseCase.join(
                jwt.userId(),
                agreeTerms = request.agreeTerms == true,
                agreePrivacy = request.agreePrivacy == true,
                marketing = request.marketing == true,
            )
        return ResponseEntity.created(URI.create("/api/v1/me/customer")).body(me)
    }
}

/** 앱: 등급 안내(로그인 없이도 볼 수 있다). */
@Tag(
    name = "등급 안내 (앱)",
    description = "커머스 웹/앱(웹뷰)이 같은 출처 /api 로 부른다(nginx → commerce-service). 로그인 없이 볼 수 있다(토큰 불필요).",
)
@RestController
@RequestMapping("/api/v1/tiers")
class TierController(
    private val publicTiersUseCase: PublicTiersUseCase,
) {
    @Operation(
        summary = "등급 안내 조회",
        description = "회원 등급(이름·색·기준 금액·적립률)을 정렬 순서대로 돌려주고, 등급마다 매월 주는 등급 쿠폰을 붙인다.",
    )
    @GetMapping
    fun tiers(): ResponseEntity<List<PublicTierResult>> = ResponseEntity.ok(publicTiersUseCase.execute())
}

/** 백오피스: 커머스 고객. */
@Tag(
    name = "고객 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/customers")
class AdminCustomerController(
    private val adminCustomerUseCase: AdminCustomerUseCase,
) {
    @Operation(
        summary = "고객 목록 검색",
        description =
            "커머스에 가입한 고객을 최근 가입 순으로 한 페이지 돌려준다(탈퇴 포함). 이름·이메일은 member-service 에서 받아 붙이고, " +
                "등급과 누적 구매 금액이 함께 온다.",
    )
    @GetMapping
    fun customers(
        @Parameter(description = "회원 id 앞부분 일치", example = "4")
        @RequestParam(required = false) q: String?,
        @Parameter(description = "등급 코드(WELCOME·SILVER·GOLD·VIP)", example = "GOLD")
        @RequestParam(required = false) tier: String?,
        @Parameter(description = "true 면 약관·개인정보에 동의한 활성 고객만, false 면 그 밖(미동의·탈퇴). 없으면 전체", example = "true")
        @RequestParam(required = false) agreed: Boolean?,
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<AdminCustomerResult>> =
        ResponseEntity.ok(PageResponse.from(adminCustomerUseCase.search(q, tier, agreed, page, size)) { it })

    /** 회원 화면 배지용. 고객인 사람만 돌려준다(최대 100명). */
    @Operation(
        summary = "고객 여부 일괄 조회",
        description =
            "회원 목록 화면의 커머스 배지용. 준 회원 id 중 커머스 고객인 사람만 상태·등급·동의 여부와 함께 돌려준다(보낸 순서대로). " +
                "101명 이상이면 400.",
    )
    @GetMapping("/lookup")
    fun lookup(
        @Parameter(description = "회원 id 목록(쉼표로 구분하거나 여러 번). 최대 100명, 공백·중복은 걷어 낸다", example = "42,57")
        @RequestParam(defaultValue = "") userIds: List<String>,
    ): ResponseEntity<List<CustomerLookupResult>> = ResponseEntity.ok(adminCustomerUseCase.lookup(userIds))

    @Operation(
        summary = "고객 상세 조회",
        description = "고객 정보(이름·이메일·상태·등급·산정 기준 금액·누적 금액)와 최근 등급 변경 이력(최대 24건)을 돌려준다. 커머스에 가입한 적 없는 회원이면 404.",
    )
    @GetMapping("/{userId}")
    fun customer(
        @Parameter(description = "회원 id(모두 계정 id)", example = "42")
        @PathVariable userId: String,
    ): ResponseEntity<AdminCustomerDetailResult> = ResponseEntity.ok(adminCustomerUseCase.detail(userId))

    /** 회원 허브 요약. 고객이 아니거나 없는 회원이어도 200(0·null). */
    @Operation(
        summary = "회원 커머스 요약 조회",
        description =
            "회원 허브 화면용. 상태별 주문 수·배송 완료 금액·최근 주문·쿠폰·찜·리뷰 수를 돌려준다. " +
                "고객이 아니거나 없는 회원이어도 200(customer 는 null, 숫자는 0).",
    )
    @GetMapping("/{userId}/summary")
    fun summary(
        @Parameter(description = "회원 id(모두 계정 id)", example = "42")
        @PathVariable userId: String,
    ): ResponseEntity<CustomerSummaryResult> = ResponseEntity.ok(adminCustomerUseCase.summary(userId))
}

@Schema(description = "회원 등급 한 줄. 코드는 고정이고 나머지를 바꾼다.")
data class TierRequest(
    @field:Schema(description = "등급 코드: WELCOME·SILVER·GOLD·VIP. 필수", example = "GOLD")
    val code: String? = null,
    @field:Schema(description = "등급 이름. 필수, 1~20자", example = "골드")
    val name: String? = null,
    @field:Schema(description = "등급 색 #RRGGBB. 필수", example = "#F59E0B")
    val color: String? = null,
    @field:Schema(description = "기준 금액(원, 최근 6개월 배송 완료 구매액). 필수, 가장 낮은 등급은 0, 위 등급일수록 커야 한다", example = "300000")
    val minAmount: Long? = null,
    @field:Schema(description = "구매 적립률(%). 필수, 0~20", example = "3")
    val earnRate: Int? = null,
    @field:Schema(description = "매월 1일 주는 쿠폰 id 최대 10개. 없으면 없음", example = "[7, 8]")
    val couponIds: List<Long>? = null,
) {
    fun toCommand(): TierCommand {
        val code = requireNotNull(code?.trim()?.takeIf { it.isNotEmpty() }) { "등급 코드가 없습니다." }
        return TierCommand(
            code = code,
            name = requireNotNull(name) { "$code: 등급 이름을 입력해 주세요." },
            color = requireNotNull(color) { "$code: 색을 골라 주세요." },
            minAmount = requireNotNull(minAmount) { "$code: 기준 금액을 입력해 주세요." },
            earnRate = requireNotNull(earnRate) { "$code: 적립률을 입력해 주세요." },
            couponIds = couponIds.orEmpty(),
        )
    }
}

/** 백오피스: 회원 등급 설정과 산정 실행. */
@Tag(
    name = "회원 등급 관리 (어드민)",
    description = "어드민 콘솔용. 게이트웨이 /commerce-service/api-admin/** 가 직원 토큰(ROLE_ADMIN, aud modu-admin)을 확인해 넘긴다.",
)
@RestController
@RequestMapping("/api-admin/v1/tiers")
class AdminTierController(
    private val adminTierUseCase: AdminTierUseCase,
) {
    @Operation(
        summary = "회원 등급 설정 조회",
        description = "등급 4개를 낮은 등급부터 돌려준다. 등급마다 기준 금액·적립률·매월 쿠폰(지운 쿠폰 제외)과 지금 그 등급인 활성 고객 수가 붙는다.",
    )
    @GetMapping
    fun tiers(): ResponseEntity<List<AdminTierResult>> = ResponseEntity.ok(adminTierUseCase.list())

    /** 4개 등급 전체를 보낸다. 코드는 고정, 가장 낮은 등급은 0원, 기준 금액은 위로 갈수록 커야 한다. */
    @Operation(
        summary = "회원 등급 설정 저장",
        description =
            "등급 4개(WELCOME·SILVER·GOLD·VIP)를 모두 한 번씩 보내 통째로 바꾸고 바뀐 설정을 돌려준다. 보낸 순서와 상관없이 등급 순서는 그대로다. " +
                "가장 낮은 등급 기준 금액은 0원, 위 등급일수록 커야 하고 어기면 400(없는 쿠폰도 400). 고객 등급은 다음 산정 때 바뀐다.",
    )
    @PutMapping
    fun update(
        @RequestBody request: List<TierRequest>,
    ): ResponseEntity<List<AdminTierResult>> = ResponseEntity.ok(adminTierUseCase.update(request.map { it.toCommand() }))

    @Operation(summary = "등급 산정 실행 이력 조회", description = "매월 1일 자동·관리자 수동 산정 실행을 최신 순으로 한 페이지 돌려준다(상태·결과 수 포함).")
    @GetMapping("/runs")
    fun runs(
        @Parameter(description = "페이지 번호(0부터). 기본 0", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기. 기본 20, 1~100 으로 자른다", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<TierRunResult>> = ResponseEntity.ok(PageResponse.from(adminTierUseCase.runs(page, size)) { it })

    /** 지금 다시 산정. 요청 스레드 밖에서 돌고 실행 행을 바로 돌려준다(202). 이미 돌고 있으면 409. */
    @Operation(
        summary = "등급 지금 다시 산정",
        description =
            "활성 고객 모두의 등급을 지난 6개월 배송 완료 금액으로 다시 정하고 이번 달 등급 쿠폰을 준다(같은 달에 다시 돌려도 쿠폰은 한 번). " +
                "요청 스레드 밖에서 돌고 실행 행을 바로 202 로 돌려준다. 이미 산정이 돌고 있으면 409.",
    )
    @PostMapping("/runs")
    fun run(): ResponseEntity<TierRunResult> = ResponseEntity.status(HttpStatus.ACCEPTED).body(adminTierUseCase.startRun())
}

/** 내부: member-service 가 모두 계정 탈퇴 때 부른다(X-Internal-Token). 없는 고객이어도 204. */
@Tag(name = "고객 (내부)", description = "서비스끼리만 호출(X-Internal-Token). 예: member-service 탈퇴 연동.")
@RestController
@RequestMapping("/api-internal/v1/customers")
class InternalCustomerController(
    private val withdrawCustomerUseCase: WithdrawCustomerUseCase,
) {
    @Operation(
        summary = "고객 탈퇴 처리",
        description =
            "모두 계정 탈퇴 때 member-service 가 부른다. 고객을 탈퇴 상태로 바꾸고 푸시 기기·알림 동의를 지운다. " +
                "커머스 고객이 아니어도 204 라 여러 번 불러도 된다. X-Internal-Token 이 없거나 틀리면 403.",
    )
    @DeleteMapping("/{userId}")
    fun withdraw(
        @Parameter(description = "탈퇴한 회원 id(모두 계정 id)", example = "42")
        @PathVariable userId: String,
    ): ResponseEntity<Void> {
        withdrawCustomerUseCase.execute(userId)
        return ResponseEntity.noContent().build()
    }
}
