package com.example.commerce.api.product.request

import com.example.commerce.application.domain.entity.OptionGroupSpec
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.SkuSpec
import com.example.commerce.application.usecase.command.ProductCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

/**
 * 백오피스 등록·수정 본문. 빠진 필드도 400 메시지로 알려 주려고 전부 nullable 로 받는다.
 * 옛 본문(images/optionGroups/skus 없음)도 받는다: images 는 [imageUrl], SKU 는 옵션 없는 하나(재고 [stock] 또는 0).
 */
data class ProductRequest(
    @field:NotBlank(message = "상품 이름을 입력해 주세요.")
    @field:Size(max = 100, message = "상품 이름은 100자 이하여야 합니다.")
    val name: String? = null,
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    val description: String? = null,
    @field:Size(max = 20_000, message = "상세 설명은 20,000자 이하여야 합니다.")
    val detail: String? = null,
    @field:NotNull(message = "가격을 입력해 주세요.")
    @field:PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
    val price: Long? = null,
    @field:PositiveOrZero(message = "정가는 0 이상이어야 합니다.")
    val listPrice: Long? = null,
    val categoryId: Long? = null,
    val status: ProductStatus? = null,
    @field:Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
    @field:Pattern(regexp = "^(https?://\\S+)?$", message = "이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.")
    val imageUrl: String? = null,
    @field:Size(max = Product.MAX_IMAGES, message = "사진은 최대 10장까지 넣을 수 있습니다.")
    val images: List<
        @Pattern(regexp = "^https?://\\S+$", message = "이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.")
        String,
    >? = null,
    @field:Valid
    @field:Size(max = Product.MAX_OPTION_GROUPS, message = "옵션 그룹은 최대 3개까지 둘 수 있습니다.")
    val optionGroups: List<OptionGroupRequest>? = null,
    @field:Valid
    val skus: List<SkuRequest>? = null,
    /** 옵션 없는 상품의 재고(옛 본문 호환). skus 가 있으면 무시한다. */
    @field:PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
    val stock: Int? = null,
) {
    /** @Valid 를 통과한 뒤에만 부른다. */
    fun toCommand(): ProductCommand {
        val groups =
            optionGroups.orEmpty().map {
                OptionGroupSpec(
                    name = it.name.orEmpty().trim(),
                    values = it.values.orEmpty().map(String::trim),
                )
            }
        val skuSpecs =
            skus?.map {
                SkuSpec(
                    options =
                        it.options
                            .orEmpty()
                            .mapKeys { (k, _) -> k.trim() }
                            .mapValues { (_, v) -> v.trim() },
                    extraPrice =
                        it.extraPrice ?: 0,
                    stock = it.stock ?: 0,
                )
            }
                ?: listOf(SkuSpec(options = emptyMap(), extraPrice = 0, stock = stock ?: 0))
        return ProductCommand(
            name = requireNotNull(name).trim(),
            description = description?.trim().orEmpty(),
            detail = detail?.trim()?.takeIf { it.isNotEmpty() },
            price = requireNotNull(price),
            listPrice = listPrice,
            categoryId = categoryId,
            status = status ?: ProductStatus.SELLING,
            images = images?.map(String::trim)?.filter { it.isNotEmpty() } ?: listOfNotNull(imageUrl?.trim()?.takeIf { it.isNotEmpty() }),
            optionGroups = groups,
            skus = skuSpecs,
        )
    }
}

data class OptionGroupRequest(
    @field:NotBlank(message = "옵션 그룹 이름을 입력해 주세요.")
    @field:Size(max = 30, message = "옵션 그룹 이름은 30자 이하여야 합니다.")
    val name: String? = null,
    @field:Size(min = 1, max = Product.MAX_OPTION_VALUES, message = "옵션 값은 그룹당 1~20개여야 합니다.")
    val values: List<
        @NotBlank(message = "옵션 값을 입력해 주세요.")
        @Size(max = 30, message = "옵션 값은 30자 이하여야 합니다.")
        String,
    >? = null,
)

data class SkuRequest(
    /** 그룹명 → 값명. 옵션 없는 상품은 빈 맵. */
    val options: Map<String, String>? = null,
    @field:PositiveOrZero(message = "추가금은 0 이상이어야 합니다.")
    val extraPrice: Long? = null,
    @field:PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
    val stock: Int? = null,
)
