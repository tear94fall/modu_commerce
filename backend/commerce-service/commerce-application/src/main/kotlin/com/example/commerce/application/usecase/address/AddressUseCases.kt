package com.example.commerce.application.usecase.address

import com.example.commerce.application.service.AddressCommandService
import com.example.commerce.application.service.AddressQueryService
import com.example.commerce.application.usecase.command.AddressCommand
import com.example.commerce.application.usecase.result.AddressResult
import org.springframework.stereotype.Component

@Component
class GetAddressesUseCase(
    private val addressQueryService: AddressQueryService,
) {
    fun execute(userId: String): List<AddressResult> = addressQueryService.all(userId).map(AddressResult::from)
}

@Component
class CreateAddressUseCase(
    private val addressCommandService: AddressCommandService,
) {
    fun execute(
        userId: String,
        command: AddressCommand,
    ): AddressResult = AddressResult.from(addressCommandService.create(userId, command))
}

@Component
class UpdateAddressUseCase(
    private val addressCommandService: AddressCommandService,
) {
    fun execute(
        userId: String,
        id: Long,
        command: AddressCommand,
    ): AddressResult = AddressResult.from(addressCommandService.update(userId, id, command))
}

@Component
class SetDefaultAddressUseCase(
    private val addressCommandService: AddressCommandService,
) {
    fun execute(
        userId: String,
        id: Long,
    ): AddressResult = AddressResult.from(addressCommandService.setDefault(userId, id))
}

@Component
class DeleteAddressUseCase(
    private val addressCommandService: AddressCommandService,
) {
    fun execute(
        userId: String,
        id: Long,
    ) = addressCommandService.delete(userId, id)
}
