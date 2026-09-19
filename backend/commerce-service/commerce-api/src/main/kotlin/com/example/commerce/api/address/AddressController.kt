package com.example.commerce.api.address

import com.example.commerce.api.common.userId
import com.example.commerce.application.usecase.address.CreateAddressUseCase
import com.example.commerce.application.usecase.address.DeleteAddressUseCase
import com.example.commerce.application.usecase.address.GetAddressesUseCase
import com.example.commerce.application.usecase.address.SetDefaultAddressUseCase
import com.example.commerce.application.usecase.address.UpdateAddressUseCase
import com.example.commerce.application.usecase.command.AddressCommand
import com.example.commerce.application.usecase.result.AddressResult
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

data class AddressRequest(
    @field:NotBlank(message = "받는 사람을 입력해 주세요.")
    @field:Size(max = 30, message = "받는 사람은 30자 이하여야 합니다.")
    val recipient: String? = null,
    @field:NotBlank(message = "연락처를 입력해 주세요.")
    @field:Pattern(regexp = "^[0-9-]{9,20}$", message = "연락처는 숫자와 하이픈만 쓸 수 있습니다.")
    val phone: String? = null,
    @field:NotBlank(message = "우편번호를 입력해 주세요.")
    @field:Pattern(regexp = "^[0-9]{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
    val zipCode: String? = null,
    @field:NotBlank(message = "주소를 입력해 주세요.")
    @field:Size(max = 100, message = "주소는 100자 이하여야 합니다.")
    val address1: String? = null,
    @field:Size(max = 100, message = "상세 주소는 100자 이하여야 합니다.")
    val address2: String? = null,
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

@RestController
@RequestMapping("/api/v1/addresses")
class AddressController(
    private val getAddressesUseCase: GetAddressesUseCase,
    private val createAddressUseCase: CreateAddressUseCase,
    private val updateAddressUseCase: UpdateAddressUseCase,
    private val setDefaultAddressUseCase: SetDefaultAddressUseCase,
    private val deleteAddressUseCase: DeleteAddressUseCase,
) {
    @GetMapping
    fun addresses(
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<List<AddressResult>> = ResponseEntity.ok(getAddressesUseCase.execute(jwt.userId()))

    @PostMapping
    fun create(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: AddressRequest,
    ): ResponseEntity<AddressResult> {
        val created = createAddressUseCase.execute(jwt.userId(), request.toCommand())
        return ResponseEntity.created(URI.create("/api/v1/addresses/${created.id}")).body(created)
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
        @Valid @RequestBody request: AddressRequest,
    ): ResponseEntity<AddressResult> = ResponseEntity.ok(updateAddressUseCase.execute(jwt.userId(), id, request.toCommand()))

    @PutMapping("/{id}/default")
    fun setDefault(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<AddressResult> = ResponseEntity.ok(setDefaultAddressUseCase.execute(jwt.userId(), id))

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        deleteAddressUseCase.execute(jwt.userId(), id)
        return ResponseEntity.noContent().build()
    }
}
