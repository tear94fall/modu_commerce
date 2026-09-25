package com.example.commerce.application.domain.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogEntityTest {
    private fun tshirt(): Product = Product.create(name = "티셔츠", description = "코튼", price = 19_000)

    private val colorSize =
        listOf(OptionGroupSpec("색상", listOf("블랙", "화이트")), OptionGroupSpec("사이즈", listOf("M", "L")))

    private fun combos(stock: Int = 10): List<SkuSpec> =
        listOf("블랙", "화이트").flatMap { c -> listOf("M", "L").map { s -> SkuSpec(mapOf("색상" to c, "사이즈" to s), 0, stock) } }

    @Test
    fun `옵션 조합으로 SKU 를 만들고 값이 그룹을 가리킨다`() {
        val product = tshirt()
        product.replaceOptions(colorSize, combos())

        assertEquals(listOf("색상", "사이즈"), product.optionGroups.map { it.name })
        assertEquals(4, product.skus.size)
        val blackM = product.skus.first { it.optionKey() == "사이즈=M|색상=블랙" }
        assertEquals("블랙 / M", blackM.optionLabel())
        assertEquals(40, product.totalStock())
        assertFalse(product.isSoldOut())
    }

    @Test
    fun `같은 조합의 SKU 는 수정 후에도 같은 인스턴스가 남는다`() {
        val product = tshirt()
        product.replaceOptions(colorSize, combos())
        val before = product.skus.first { it.optionKey() == "사이즈=L|색상=화이트" }

        // 색상에 네이비를 더하고 화이트 L 의 재고를 바꾼다
        val groups = listOf(OptionGroupSpec("색상", listOf("블랙", "화이트", "네이비")), OptionGroupSpec("사이즈", listOf("M", "L")))
        val skus =
            combos().map { if (it.options["색상"] == "화이트" && it.options["사이즈"] == "L") it.copy(stock = 3, extraPrice = 500) else it } +
                SkuSpec(mapOf("색상" to "네이비", "사이즈" to "M"), 0, 1)
        product.replaceOptions(groups, skus)

        val after = product.skus.first { it.optionKey() == "사이즈=L|색상=화이트" }
        assertSame(before, after)
        assertEquals(3, after.stock)
        assertEquals(500L, after.extraPrice)
        assertEquals(5, product.skus.size)
        // 새 값 객체를 가리킨다
        assertTrue(after.optionValues.all { value -> product.optionGroups.any { it.values.contains(value) } })
    }

    @Test
    fun `조합 규칙을 어기면 IllegalArgumentException`() {
        val product = tshirt()

        fun fails(
            groups: List<OptionGroupSpec>,
            skus: List<SkuSpec>,
            message: String,
        ) {
            val ex = assertThrows(IllegalArgumentException::class.java) { product.replaceOptions(groups, skus) }
            assertEquals(message, ex.message)
        }
        fails(colorSize, emptyList(), "옵션 조합(SKU)을 하나 이상 입력해 주세요.")
        fails(colorSize, listOf(SkuSpec(mapOf("색상" to "블랙"))), "옵션 조합은 모든 그룹의 값을 하나씩 가져야 합니다.")
        fails(colorSize, listOf(SkuSpec(mapOf("색상" to "레드", "사이즈" to "M"))), "옵션 '색상' 에 '레드' 값이 없습니다.")
        fails(colorSize, combos() + combos().first(), "같은 옵션 조합이 두 번 들어 있습니다.")
        fails(colorSize, combos(stock = -1), "재고는 0 이상이어야 합니다.")
        fails(listOf(OptionGroupSpec("색상", listOf("블랙", "블랙"))), listOf(SkuSpec(mapOf("색상" to "블랙"))), "옵션 '색상' 의 값이 겹칩니다.")
        fails(
            List(4) { OptionGroupSpec("g$it", listOf("v")) },
            listOf(SkuSpec((0..3).associate { "g$it" to "v" })),
            "옵션 그룹은 최대 3개까지 둘 수 있습니다.",
        )
    }

    @Test
    fun `옵션 없는 상품은 값 없는 SKU 하나로 재고를 든다`() {
        val product = tshirt()
        product.replaceOptions(emptyList(), listOf(SkuSpec(emptyMap(), 0, 0)))
        assertEquals(1, product.skus.size)
        assertEquals("", product.skus.single().optionLabel())
        assertTrue(product.isSoldOut())

        val fresh = tshirt()
        fresh.ensureDefaultSku(stock = 5)
        fresh.ensureDefaultSku(stock = 99)
        assertEquals(5, fresh.totalStock())
    }

    @Test
    fun `사진은 첫 장이 대표 사진이 되고 10장을 넘기면 거부한다`() {
        val product = tshirt()
        product.replaceImages(listOf("https://img/1", "https://img/2"))
        assertEquals("https://img/1", product.imageUrl)
        assertEquals(listOf(0, 1), product.images.map { it.sortOrder })
        product.replaceImages(emptyList())
        assertEquals(null, product.imageUrl)
        assertThrows(IllegalArgumentException::class.java) { product.replaceImages(List(11) { "https://img/$it" }) }
    }

    @Test
    fun `할인율은 정가가 판매가보다 클 때만 계산한다`() {
        val product = tshirt()
        assertEquals(0, product.discountRate())
        product.updateCatalog(null, 25_000, null, ProductStatus.SELLING)
        assertEquals(24, product.discountRate())
        product.updateCatalog(null, 19_000, null, ProductStatus.SELLING)
        assertEquals(0, product.discountRate())
        assertThrows(IllegalArgumentException::class.java) { product.updateCatalog(null, 10_000, null, ProductStatus.SELLING) }
    }

    @Test
    fun `카테고리 경로는 상위부터 자기까지`() {
        val root = Category.create("패션")
        val child = Category.create("의류", root)
        assertEquals(listOf("패션"), root.path().map { it.name })
        assertEquals(listOf("패션", "의류"), child.path().map { it.name })
        assertTrue(root.isRoot())
        assertFalse(child.isRoot())
    }

    @Test
    fun `찜 수는 0 아래로 내려가지 않는다`() {
        val product = tshirt()
        product.decreaseWishCount()
        product.increaseWishCount()
        product.increaseWishCount()
        product.decreaseWishCount()
        assertEquals(1L, product.wishCount)
    }
}
