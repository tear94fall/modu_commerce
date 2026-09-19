package com.example.commerce.application.domain.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/** 옵션 그룹 하나를 만드는 재료. */
data class OptionGroupSpec(
    val name: String,
    val values: List<String>,
)

/** SKU 하나를 만드는 재료. [options] 는 그룹명 → 값명. 옵션 없는 상품은 빈 맵. */
data class SkuSpec(
    val options: Map<String, String>,
    val extraPrice: Long = 0,
    val stock: Int = 0,
)

/**
 * 삭제는 deleted_at 만 채운다(소프트 삭제). @SQLRestriction 이 조회·검색·단건 어디서든
 * 삭제된 행을 빼므로 쿼리마다 조건을 넣지 않는다. 삭제 행까지 세야 하면 네이티브 쿼리를 쓴다.
 *
 * 사진·옵션·SKU 는 상품이 소유한다(cascade + orphanRemoval). 조합 규칙은 [replaceOptions] 가 지킨다.
 */
@Entity
@Table(name = "products")
@SQLRestriction("deleted_at IS NULL")
class Product(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    /** 짧은 소개. 목록·상세 상단에 보인다. */
    @Column(name = "description", nullable = false, length = 500)
    var description: String,
    /** 판매가(원). */
    @Column(name = "price", nullable = false)
    var price: Long,
    /** 대표 사진. 항상 images 의 첫 장과 같다. 옛 컬럼이라 이름을 유지한다. */
    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,
) : BaseEntity() {
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category? = null

    /** 정가(원). 판매가보다 크면 할인율을 보여 준다. */
    @Column(name = "list_price")
    var listPrice: Long? = null

    /** 긴 상세 설명. */
    @Column(name = "detail", columnDefinition = "MEDIUMTEXT")
    var detail: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "varchar(20) not null default 'SELLING'")
    var status: ProductStatus = ProductStatus.SELLING

    /** 찜 수 캐시. 인기순 정렬에 쓴다. */
    @Column(name = "wish_count", nullable = false, columnDefinition = "bigint not null default 0")
    var wishCount: Long = 0

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    val images: MutableList<ProductImage> = mutableListOf()

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    val optionGroups: MutableList<ProductOptionGroup> = mutableListOf()

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("id ASC")
    val skus: MutableList<ProductSku> = mutableListOf()

    fun update(
        name: String,
        description: String,
        price: Long,
        imageUrl: String?,
    ) {
        this.name = name
        this.description = description
        this.price = price
        this.imageUrl = imageUrl
    }

    fun updateCatalog(
        category: Category?,
        listPrice: Long?,
        detail: String?,
        status: ProductStatus,
    ) {
        require(listPrice == null || listPrice >= price) { "정가는 판매가 이상이어야 합니다." }
        this.category = category
        this.listPrice = listPrice
        this.detail = detail
        this.status = status
    }

    /** 사진 목록을 통째로 바꾼다. 첫 장이 대표 사진이 된다. */
    fun replaceImages(urls: List<String>) {
        require(urls.size <= MAX_IMAGES) { "사진은 최대 ${MAX_IMAGES}장까지 넣을 수 있습니다." }
        images.clear()
        urls.forEachIndexed { index, url -> images.add(ProductImage(product = this, url = url, sortOrder = index)) }
        imageUrl = urls.firstOrNull()
    }

    /**
     * 옵션 그룹·값과 SKU 를 통째로 바꾼다. 같은 조합의 SKU 는 기존 인스턴스(id)를 유지해
     * 장바구니·주문이 가리키는 id 가 살아남는다. 규칙 위반은 IllegalArgumentException(→ 400).
     */
    fun replaceOptions(
        groups: List<OptionGroupSpec>,
        skuSpecs: List<SkuSpec>,
    ) {
        validateOptions(groups, skuSpecs)

        // 기존 SKU 의 조합 키는 옛 값 객체에서 읽어야 하므로 그룹을 지우기 전에 계산한다.
        val existingByKey = skus.associateBy { it.optionKey() }

        optionGroups.clear()
        val valueByKey = mutableMapOf<Pair<String, String>, ProductOptionValue>()
        groups.forEachIndexed { groupIndex, spec ->
            val group = ProductOptionGroup(product = this, name = spec.name, sortOrder = groupIndex)
            spec.values.forEachIndexed { valueIndex, valueName ->
                valueByKey[spec.name to valueName] = group.addValue(valueName, valueIndex)
            }
            optionGroups.add(group)
        }

        val kept =
            skuSpecs.map { spec ->
                val values = spec.options.map { (groupName, valueName) -> valueByKey.getValue(groupName to valueName) }
                val existing = existingByKey[ProductSku.optionKeyOf(spec.options)]
                if (existing != null) {
                    existing.optionValues.clear()
                    existing.optionValues.addAll(values)
                    existing.update(spec.extraPrice, spec.stock)
                    existing
                } else {
                    ProductSku(product = this, optionValues = values.toMutableSet(), extraPrice = spec.extraPrice, stock = spec.stock)
                }
            }
        skus.retainAll(kept.toSet())
        kept.forEach { if (it !in skus) skus.add(it) }
    }

    /** 옵션이 없는 새 상품에 재고만 있는 SKU 하나를 둔다. 이미 SKU 가 있으면 아무것도 안 한다. */
    fun ensureDefaultSku(stock: Int = 0) {
        if (skus.isEmpty()) skus.add(ProductSku(product = this, stock = stock))
    }

    fun delete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }

    fun isDeleted(): Boolean = deletedAt != null

    fun isSoldOut(): Boolean = skus.isEmpty() || skus.all { it.isSoldOut() }

    fun totalStock(): Int = skus.sumOf { it.stock }

    /** 정가 대비 할인율(%). 정가가 없거나 판매가 이하이면 0. */
    fun discountRate(): Int {
        val list = listPrice ?: return 0
        if (list <= price || list <= 0) return 0
        return ((list - price) * 100 / list).toInt()
    }

    fun increaseWishCount() {
        wishCount += 1
    }

    fun decreaseWishCount() {
        if (wishCount > 0) wishCount -= 1
    }

    override fun toString(): String = "Product(id=$id, name='$name', price=$price)"

    private fun validateOptions(
        groups: List<OptionGroupSpec>,
        skuSpecs: List<SkuSpec>,
    ) {
        require(groups.size <= MAX_OPTION_GROUPS) { "옵션 그룹은 최대 ${MAX_OPTION_GROUPS}개까지 둘 수 있습니다." }
        require(groups.all { it.name.isNotBlank() }) { "옵션 그룹 이름을 입력해 주세요." }
        require(groups.map { it.name }.toSet().size == groups.size) { "옵션 그룹 이름이 겹칩니다." }
        groups.forEach { group ->
            require(group.values.isNotEmpty()) { "옵션 '${group.name}' 의 값을 하나 이상 입력해 주세요." }
            require(group.values.size <= MAX_OPTION_VALUES) { "옵션 값은 그룹당 최대 ${MAX_OPTION_VALUES}개까지 둘 수 있습니다." }
            require(group.values.all { it.isNotBlank() }) { "옵션 '${group.name}' 에 빈 값이 있습니다." }
            require(group.values.toSet().size == group.values.size) { "옵션 '${group.name}' 의 값이 겹칩니다." }
        }
        require(skuSpecs.isNotEmpty()) { "옵션 조합(SKU)을 하나 이상 입력해 주세요." }
        val groupNames = groups.map { it.name }.toSet()
        val valuesByGroup = groups.associate { it.name to it.values.toSet() }
        skuSpecs.forEach { spec ->
            require(spec.options.keys == groupNames) { "옵션 조합은 모든 그룹의 값을 하나씩 가져야 합니다." }
            spec.options.forEach { (groupName, valueName) ->
                require(valueName in valuesByGroup.getValue(groupName)) { "옵션 '$groupName' 에 '$valueName' 값이 없습니다." }
            }
            require(spec.extraPrice >= 0) { "추가금은 0 이상이어야 합니다." }
            require(spec.stock >= 0) { "재고는 0 이상이어야 합니다." }
        }
        val keys = skuSpecs.map { ProductSku.optionKeyOf(it.options) }
        require(keys.toSet().size == keys.size) { "같은 옵션 조합이 두 번 들어 있습니다." }
    }

    companion object {
        const val MAX_IMAGES = 10
        const val MAX_OPTION_GROUPS = 3
        const val MAX_OPTION_VALUES = 20

        fun create(
            name: String,
            description: String,
            price: Long,
            imageUrl: String? = null,
        ): Product =
            Product(
                name = name,
                description = description,
                price = price,
                imageUrl = imageUrl,
            )
    }
}
