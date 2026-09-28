package com.example.commerce.api.product.request

import com.example.commerce.application.domain.entity.OptionGroupSpec
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.SkuSpec
import com.example.commerce.application.usecase.command.ProductCommand
import io.swagger.v3.oas.annotations.media.Schema
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
@Schema(
    description =
        "상품 등록·수정 본문. 옵션 없는 상품은 skus 에 options 가 빈 SKU 하나를 보낸다. " +
            "옛 본문(images·optionGroups·skus 없이 imageUrl·stock)도 받는다.",
)
data class ProductRequest(
    @field:Schema(description = "상품 이름. 필수, 100자 이하", example = "스테인리스 텀블러 500ml")
    @field:NotBlank(message = "상품 이름을 입력해 주세요.")
    @field:Size(max = 100, message = "상품 이름은 100자 이하여야 합니다.")
    val name: String? = null,
    @field:Schema(description = "짧은 설명(목록·검색에 쓰인다). 500자 이하", example = "보온·보냉 12시간")
    @field:Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    val description: String? = null,
    @field:Schema(description = "상세 설명(상세 화면 본문). 20,000자 이하, 빈 값이면 없음", example = "용량 500ml, 무게 280g")
    @field:Size(max = 20_000, message = "상세 설명은 20,000자 이하여야 합니다.")
    val detail: String? = null,
    @field:Schema(description = "판매가(원). 필수, 0 이상", example = "15900")
    @field:NotNull(message = "가격을 입력해 주세요.")
    @field:PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
    val price: Long? = null,
    @field:Schema(description = "정가(원, 할인 전 가격). 선택, 판매가 이상이어야 한다", example = "19900")
    @field:PositiveOrZero(message = "정가는 0 이상이어야 합니다.")
    val listPrice: Long? = null,
    @field:Schema(description = "카테고리 id. 없으면 미분류", example = "3")
    val categoryId: Long? = null,
    @field:Schema(description = "판매 상태: SELLING(판매 중), HIDDEN(숨김). 없으면 SELLING", example = "SELLING")
    val status: ProductStatus? = null,
    @field:Schema(description = "(옛 본문) 대표 이미지 주소 http(s)://… 500자 이하. images 가 있으면 무시", example = "https://example.com/p/1.jpg")
    @field:Size(max = 500, message = "이미지 주소는 500자 이하여야 합니다.")
    @field:Pattern(regexp = "^(https?://\\S+)?$", message = "이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.")
    val imageUrl: String? = null,
    @field:Schema(description = "사진 주소 목록(최대 10장, 첫 장이 대표). 각각 http(s):// 로 시작해야 한다")
    @field:Size(max = Product.MAX_IMAGES, message = "사진은 최대 10장까지 넣을 수 있습니다.")
    val images: List<
        @Pattern(regexp = "^https?://\\S+$", message = "이미지 주소는 http:// 또는 https:// 로 시작해야 합니다.")
        String,
    >? = null,
    @field:Schema(description = "옵션 그룹(최대 3개, 예: 색상·사이즈). 없으면 옵션 없는 상품")
    @field:Valid
    @field:Size(max = Product.MAX_OPTION_GROUPS, message = "옵션 그룹은 최대 3개까지 둘 수 있습니다.")
    val optionGroups: List<OptionGroupRequest>? = null,
    @field:Schema(
        description =
            "옵션 조합(SKU)별 추가금·재고. 모든 그룹의 값을 하나씩 가져야 하고 같은 조합은 한 번만. " +
                "없으면 옵션 없는 SKU 하나(재고 stock)를 만든다",
    )
    @field:Valid
    val skus: List<SkuRequest>? = null,
    /** 옵션 없는 상품의 재고(옛 본문 호환). skus 가 있으면 무시한다. */
    @field:Schema(description = "(옛 본문) 옵션 없는 상품의 재고, 0 이상. skus 가 있으면 무시", example = "100")
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

@Schema(description = "옵션 그룹 하나. 그룹 이름과 값은 상품 안에서 겹치면 안 된다.")
data class OptionGroupRequest(
    @field:Schema(description = "그룹 이름. 필수, 30자 이하", example = "색상")
    @field:NotBlank(message = "옵션 그룹 이름을 입력해 주세요.")
    @field:Size(max = 30, message = "옵션 그룹 이름은 30자 이하여야 합니다.")
    val name: String? = null,
    @field:Schema(description = "옵션 값 1~20개. 각각 30자 이하, 빈 값 불가", example = "[\"블랙\", \"화이트\"]")
    @field:Size(min = 1, max = Product.MAX_OPTION_VALUES, message = "옵션 값은 그룹당 1~20개여야 합니다.")
    val values: List<
        @NotBlank(message = "옵션 값을 입력해 주세요.")
        @Size(max = 30, message = "옵션 값은 30자 이하여야 합니다.")
        String,
    >? = null,
)

@Schema(description = "옵션 조합(SKU) 하나.")
data class SkuRequest(
    /** 그룹명 → 값명. 옵션 없는 상품은 빈 맵. */
    @field:Schema(description = "그룹 이름 → 값 이름. 옵션 없는 상품은 빈 맵", example = "{\"색상\": \"블랙\"}")
    val options: Map<String, String>? = null,
    @field:Schema(description = "판매가에 더하는 추가금(원), 0 이상. 없으면 0", example = "1000")
    @field:PositiveOrZero(message = "추가금은 0 이상이어야 합니다.")
    val extraPrice: Long? = null,
    @field:Schema(description = "재고, 0 이상. 없으면 0", example = "30")
    @field:PositiveOrZero(message = "재고는 0 이상이어야 합니다.")
    val stock: Int? = null,
)
