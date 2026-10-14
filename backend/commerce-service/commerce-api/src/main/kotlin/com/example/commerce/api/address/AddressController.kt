package com.example.commerce.api.address

import com.example.commerce.api.common.CustomerRequired
import com.example.commerce.api.common.userId
import com.example.commerce.application.usecase.address.CreateAddressUseCase
import com.example.commerce.application.usecase.address.DeleteAddressUseCase
import com.example.commerce.application.usecase.address.GetAddressesUseCase
import com.example.commerce.application.usecase.address.SetDefaultAddressUseCase
import com.example.commerce.application.usecase.address.UpdateAddressUseCase
import com.example.commerce.application.usecase.command.AddressCommand
import com.example.commerce.application.usecase.result.AddressResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
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
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@Schema(description = "배송지 등록·수정 요청. 문자열은 앞뒤 공백을 지우고 저장한다.")
data class AddressRequest(
    @field:Schema(description = "받는 사람. 필수, 30자 이하", example = "홍길동")
    @field:NotBlank(message = "받는 사람을 입력해 주세요.")
    @field:Size(max = 30, message = "받는 사람은 30자 이하여야 합니다.")
    val recipient: String? = null,
    @field:Schema(description = "연락처. 필수, 숫자와 하이픈만 9~20자", example = "010-1234-5678")
    @field:NotBlank(message = "연락처를 입력해 주세요.")
    @field:Pattern(regexp = "^[0-9-]{9,20}$", message = "연락처는 숫자와 하이픈만 쓸 수 있습니다.")
    val phone: String? = null,
    @field:Schema(description = "우편번호. 필수, 숫자 5자리", example = "06236")
    @field:NotBlank(message = "우편번호를 입력해 주세요.")
    @field:Pattern(regexp = "^[0-9]{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
    val zipCode: String? = null,
    @field:Schema(description = "기본 주소. 필수, 100자 이하", example = "서울특별시 강남구 테헤란로 123")
    @field:NotBlank(message = "주소를 입력해 주세요.")
    @field:Size(max = 100, message = "주소는 100자 이하여야 합니다.")
    val address1: String? = null,
    @field:Schema(description = "상세 주소. 선택, 100자 이하. 비우면 없음", example = "4층 401호")
    @field:Size(max = 100, message = "상세 주소는 100자 이하여야 합니다.")
    val address2: String? = null,
    @field:Schema(description = "기본 배송지로 할지. 선택, 기본 false. 첫 배송지는 이 값과 관계없이 기본이 된다", example = "false")
    val isDefault: Boolean? = null,
) {
    fun toCommand() =
        AddressCommand(
            recipient = requireNotNull(recipient).trim(),
            phone = requireNotNull(phone).trim(),
            zipCode = requireNotNull(zipCode).trim(),
            address1 = requireNotNull(address1).trim(),
            address2 = address2?.trim()?.takeIf { it.isNotEmpty() },
            isDefault = isDefault ?: false,
        )
}

@Tag(
    name = "배송지 (앱)",
    description =
        "커머스 웹/앱(웹뷰)이 API 게이트웨이 /commerce-service/api-public/** 로 부른다(토큰은 게이트웨이가 보고 서비스가 다시 본다). " +
            "모두 계정 토큰(aud modu-commerce) 필요. 모두의 커머스 가입(약관 동의) 필요, 아니면 403 CUSTOMER_REQUIRED.",
)
@CustomerRequired
@RestController
@RequestMapping("/api-public/v1/addresses")
class AddressController(
    private val getAddressesUseCase: GetAddressesUseCase,
    private val createAddressUseCase: CreateAddressUseCase,
    private val updateAddressUseCase: UpdateAddressUseCase,
    private val setDefaultAddressUseCase: SetDefaultAddressUseCase,
    private val deleteAddressUseCase: DeleteAddressUseCase,
) {
    @Operation(summary = "배송지 목록 조회", description = "내 배송지를 기본 배송지 먼저, 그다음 최근 등록 순으로 돌려준다.")
    @GetMapping
    fun addresses(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<List<AddressResult>> = ResponseEntity.ok(getAddressesUseCase.execute(jwt.userId()))

    @Operation(
        summary = "배송지 등록",
        description =
            "배송지를 추가하고 201 과 Location 을 돌려준다. isDefault 이거나 첫 배송지면 기본 배송지가 되고 다른 배송지의 기본 표시는 풀린다. " +
                "입력 형식이 틀리면 400.",
    )
    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: AddressRequest,
    ): ResponseEntity<AddressResult> {
        val created = createAddressUseCase.execute(jwt.userId(), request.toCommand())
        return ResponseEntity.created(URI.create("/api-public/v1/addresses/${created.id}")).body(created)
    }

    @Operation(
        summary = "배송지 수정",
        description = "내 배송지 내용을 통째로 바꾼다. isDefault=true 면 기본 배송지로도 바꾼다(false 로 기본을 풀지는 않는다). 내 배송지가 아니면 404.",
    )
    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "배송지 id", example = "1")
        @PathVariable id: Long,
        @Valid @RequestBody request: AddressRequest,
    ): ResponseEntity<AddressResult> = ResponseEntity.ok(updateAddressUseCase.execute(jwt.userId(), id, request.toCommand()))

    @Operation(summary = "기본 배송지 지정", description = "이 배송지를 기본으로 하고 나머지의 기본 표시를 푼다. 내 배송지가 아니면 404.")
    @PutMapping("/{id}/default")
    fun setDefault(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "배송지 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<AddressResult> = ResponseEntity.ok(setDefaultAddressUseCase.execute(jwt.userId(), id))

    @Operation(
        summary = "배송지 삭제",
        description = "내 배송지를 지우고 204. 기본 배송지를 지우면 남은 것 중 가장 최근 배송지가 기본이 된다. 내 배송지가 아니면 404.",
    )
    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @Parameter(description = "배송지 id", example = "1")
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteAddressUseCase.execute(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }
}
