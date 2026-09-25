package com.example.commerce.application.service

import com.example.commerce.application.domain.entity.Address
import com.example.commerce.application.domain.repository.ro.AddressRoRepository
import com.example.commerce.application.domain.repository.rw.AddressRwRepository
import com.example.commerce.application.usecase.command.AddressCommand
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(transactionManager = "roTransactionManager", readOnly = true)
class AddressQueryService(
    private val addressRoRepository: AddressRoRepository,
) {
    fun all(userId: String): List<Address> = addressRoRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(userId)
}

/** 기본 배송지는 항상 하나다. 첫 배송지는 자동으로 기본이 되고, 기본을 지우면 가장 최근 것이 기본이 된다. */
@Service
@Transactional(transactionManager = "rwTransactionManager")
class AddressCommandService(
    private val addressRwRepository: AddressRwRepository,
) {
    fun create(
        userId: String,
        command: AddressCommand,
    ): Address {
        val existing = addressRwRepository.findAllByUserId(userId)
        val makeDefault = command.isDefault || existing.isEmpty()
        if (makeDefault) existing.forEach { it.isDefault = false }
        return addressRwRepository.save(
            Address(
                userId = userId,
                recipient = command.recipient,
                phone = command.phone,
                zipCode = command.zipCode,
                address1 = command.address1,
                address2 = command.address2,
                isDefault = makeDefault,
            ),
        )
    }

    fun update(
        userId: String,
        id: Long,
        command: AddressCommand,
    ): Address {
        val address = own(userId, id)
        address.update(command.recipient, command.phone, command.zipCode, command.address1, command.address2)
        if (command.isDefault) setDefault(userId, id)
        return address
    }

    fun setDefault(
        userId: String,
        id: Long,
    ): Address {
        val target = own(userId, id)
        addressRwRepository.findAllByUserId(userId).forEach { it.isDefault = it.id == id }
        return target
    }

    fun delete(
        userId: String,
        id: Long,
    ) {
        val address = own(userId, id)
        addressRwRepository.delete(address)
        if (address.isDefault) {
            addressRwRepository
                .findAllByUserId(userId)
                .filter { it.id != id }
                .maxByOrNull { requireNotNull(it.id) }
                ?.isDefault = true
        }
    }

    fun own(
        userId: String,
        id: Long,
    ): Address = addressRwRepository.findByIdAndUserId(id, userId) ?: throw EntityNotFoundException("id: $id 에 해당하는 배송지가 없습니다.")
}
