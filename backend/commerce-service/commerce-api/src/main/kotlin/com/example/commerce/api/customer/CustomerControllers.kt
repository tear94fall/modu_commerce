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

data class JoinCustomerRequest(
    val agreeTerms: Boolean? = null,
    val agreePrivacy: Boolean? = null,
    /** 혜택·이벤트 알림(선택). true 면 광고성 푸시 동의(야간 제외). */
    val marketing: Boolean? = null,
)

/** 앱: 커머스 가입(약관 동의)과 내 등급. */
@RestController
@RequestMapping("/api/v1/me/customer")
class MyCustomerController(
    private val myCustomerUseCase: MyCustomerUseCase,
) {
    /** 가입하지 않았거나 탈퇴했거나 동의 전이면 404 `{message, code:"CUSTOMER_REQUIRED"}`. */
    @GetMapping
    fun me(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(myCustomerUseCase.get(jwt.userId()))
        } catch (e: CustomerRequiredException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(customerRequired())
        }

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
@RestController
@RequestMapping("/api/v1/tiers")
class TierController(
    private val publicTiersUseCase: PublicTiersUseCase,
) {
    @GetMapping
    fun tiers(): ResponseEntity<List<PublicTierResult>> = ResponseEntity.ok(publicTiersUseCase.execute())
}

/** 백오피스: 커머스 고객. */
@RestController
@RequestMapping("/api-admin/v1/customers")
class AdminCustomerController(
    private val adminCustomerUseCase: AdminCustomerUseCase,
) {
    @GetMapping
    fun customers(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) tier: String?,
        @RequestParam(required = false) agreed: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<AdminCustomerResult>> =
        ResponseEntity.ok(PageResponse.from(adminCustomerUseCase.search(q, tier, agreed, page, size)) { it })

    /** 회원 화면 배지용. 고객인 사람만 돌려준다(최대 100명). */
    @GetMapping("/lookup")
    fun lookup(
        @RequestParam(defaultValue = "") userIds: List<String>,
    ): ResponseEntity<List<CustomerLookupResult>> = ResponseEntity.ok(adminCustomerUseCase.lookup(userIds))

    @GetMapping("/{userId}")
    fun customer(
        @PathVariable userId: String,
    ): ResponseEntity<AdminCustomerDetailResult> = ResponseEntity.ok(adminCustomerUseCase.detail(userId))

    /** 회원 허브 요약. 고객이 아니거나 없는 회원이어도 200(0·null). */
    @GetMapping("/{userId}/summary")
    fun summary(
        @PathVariable userId: String,
    ): ResponseEntity<CustomerSummaryResult> = ResponseEntity.ok(adminCustomerUseCase.summary(userId))
}

data class TierRequest(
    val code: String? = null,
    val name: String? = null,
    val color: String? = null,
    val minAmount: Long? = null,
    val earnRate: Int? = null,
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
@RestController
@RequestMapping("/api-admin/v1/tiers")
class AdminTierController(
    private val adminTierUseCase: AdminTierUseCase,
) {
    @GetMapping
    fun tiers(): ResponseEntity<List<AdminTierResult>> = ResponseEntity.ok(adminTierUseCase.list())

    /** 4개 등급 전체를 보낸다. 코드는 고정, 가장 낮은 등급은 0원, 기준 금액은 위로 갈수록 커야 한다. */
    @PutMapping
    fun update(
        @RequestBody request: List<TierRequest>,
    ): ResponseEntity<List<AdminTierResult>> = ResponseEntity.ok(adminTierUseCase.update(request.map { it.toCommand() }))

    @GetMapping("/runs")
    fun runs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<PageResponse<TierRunResult>> = ResponseEntity.ok(PageResponse.from(adminTierUseCase.runs(page, size)) { it })

    /** 지금 다시 산정. 요청 스레드 밖에서 돌고 실행 행을 바로 돌려준다(202). 이미 돌고 있으면 409. */
    @PostMapping("/runs")
    fun run(): ResponseEntity<TierRunResult> = ResponseEntity.status(HttpStatus.ACCEPTED).body(adminTierUseCase.startRun())
}

/** 내부: member-service 가 모두 계정 탈퇴 때 부른다(X-Internal-Token). 없는 고객이어도 204. */
@RestController
@RequestMapping("/api-internal/v1/customers")
class InternalCustomerController(
    private val withdrawCustomerUseCase: WithdrawCustomerUseCase,
) {
    @DeleteMapping("/{userId}")
    fun withdraw(
        @PathVariable userId: String,
    ): ResponseEntity<Void> {
        withdrawCustomerUseCase.execute(userId)
        return ResponseEntity.noContent().build()
    }
}
