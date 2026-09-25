package com.example.commerce.application.seed

import com.example.commerce.application.common.logger
import com.example.commerce.application.domain.entity.Category
import com.example.commerce.application.domain.entity.OptionGroupSpec
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.SkuSpec
import com.example.commerce.application.domain.repository.rw.CategoryRwRepository
import com.example.commerce.application.domain.repository.rw.ProductRwRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 테스트용 카테고리 14개(상위 4 + 하위 10)와 상품 24개. 삭제된 행까지 포함해 상품 테이블이 비어 있을 때만 넣는다. */
@Component
class ProductSeeder(
    private val productRwRepository: ProductRwRepository,
    private val categoryRwRepository: CategoryRwRepository,
) : ApplicationRunner {
    @Transactional(transactionManager = "rwTransactionManager")
    override fun run(args: ApplicationArguments) {
        if (productRwRepository.countIncludingDeleted() > 0) return
        val categories = categoryRwRepository.saveAll(sampleCategories())
        productRwRepository.saveAll(sampleProducts(categories.associateBy { it.name }))
        logger.info { "테스트 카테고리 ${categories.size}개, 상품 ${SAMPLE_COUNT}개를 넣었습니다." }
    }

    companion object {
        const val SAMPLE_COUNT = 24
        const val ROOT_CATEGORY_COUNT = 4
        const val CHILD_CATEGORY_COUNT = 10

        /** 상위 4개 뒤에 하위 10개. 하위는 앞의 상위 객체를 가리키므로 이 순서대로 저장하면 된다. */
        fun sampleCategories(): List<Category> {
            val roots =
                listOf("전자기기", "생활", "패션", "문구").mapIndexed { i, name -> Category.create(name, sortOrder = i) }
            val (electronics, living, fashion, stationery) = roots
            val children =
                listOf(
                    Category.create("이어폰·스피커", electronics, 0),
                    Category.create("키보드·마우스", electronics, 1),
                    Category.create("충전", electronics, 2),
                    Category.create("주방", living, 0),
                    Category.create("욕실", living, 1),
                    Category.create("홈데코", living, 2),
                    Category.create("가방", fashion, 0),
                    Category.create("의류", fashion, 1),
                    Category.create("모자", fashion, 2),
                    Category.create("노트·데스크", stationery, 0),
                )
            return (roots + children).onEach { c -> SAMPLE_ICONS[c.name]?.let { (icon, color) -> c.decorate(icon, color) } }
        }

        /** 샘플 카테고리 아이콘(이모지)과 타일 색(파스텔). */
        private val SAMPLE_ICONS =
            mapOf(
                "전자기기" to ("💻" to "#DBEAFE"),
                "생활" to ("🏠" to "#DCFCE7"),
                "패션" to ("👕" to "#FCE7F3"),
                "문구" to ("✏️" to "#FEF3C7"),
                "이어폰·스피커" to ("🎧" to "#E0E7FF"),
                "키보드·마우스" to ("⌨️" to "#E0F2FE"),
                "충전" to ("🔋" to "#DCFCE7"),
                "주방" to ("🍳" to "#FFEDD5"),
                "욕실" to ("🛁" to "#CFFAFE"),
                "홈데코" to ("🪴" to "#D1FAE5"),
                "가방" to ("👜" to "#FCE7F3"),
                "의류" to ("👚" to "#FAE8FF"),
                "모자" to ("🧢" to "#EDE9FE"),
                "노트·데스크" to ("📓" to "#FEF9C3"),
            )

        /**
         * 사진은 picsum.photos 의 seed 주소를 쓴다. seed 가 같으면 같은 사진이 오므로 앱 목록이 매번 바뀌지 않는다.
         * [categories] 에 없는 이름은 카테고리 없이 만든다(테스트가 카테고리 없이 저장할 때).
         */
        fun sampleProducts(categories: Map<String, Category> = emptyMap()): List<Product> {
            fun cat(name: String) = categories[name]
            val color = OptionGroupSpec("색상", listOf("블랙", "화이트"))
            return listOf(
                product(
                    cat("이어폰·스피커"),
                    "모두 무선 이어폰",
                    "가볍고 통화가 또렷한 무선 이어폰",
                    89_000,
                    109_000,
                    "earbuds",
                    "하루 종일 써도 귀가 편한 6g 초경량 설계. 통화 노이즈 캔슬링과 24시간 재생.",
                    groups = listOf(color),
                    skus = listOf(SkuSpec(mapOf("색상" to "블랙"), 0, 30), SkuSpec(mapOf("색상" to "화이트"), 0, 12)),
                ),
                product(
                    cat("이어폰·스피커"),
                    "모두 블루투스 스피커",
                    "욕실에서도 쓰는 방수 스피커",
                    49_000,
                    null,
                    "speaker",
                    "IPX7 방수, 12시간 재생, 두 대 스테레오 페어링.",
                    stock = 25,
                ),
                product(
                    cat("키보드·마우스"),
                    "모두 기계식 키보드",
                    "저소음 적축, 한글 각인 텐키리스",
                    129_000,
                    149_000,
                    "keyboard",
                    "PBT 이중사출 키캡과 핫스왑 스위치. 유무선 겸용.",
                    stock = 18,
                ),
                product(
                    cat("키보드·마우스"),
                    "모두 무선 마우스",
                    "조용한 클릭, 3기기 연결",
                    39_000,
                    null,
                    "mouse",
                    "정숙 클릭 스위치와 3기기 멀티 페어링. 한 번 충전으로 3개월.",
                    stock = 40,
                ),
                product(
                    cat("충전"),
                    "모두 고속 충전기 65W",
                    "노트북까지 충전하는 GaN 충전기",
                    45_000,
                    55_000,
                    "charger",
                    "USB-C 2포트 + USB-A. 접이식 플러그.",
                    stock = 50,
                ),
                product(cat("충전"), "모두 보조배터리 10000", "얇고 가벼운 10000mAh", 29_000, null, "powerbank", "22.5W 고속 충전, 두 기기 동시 충전.", stock = 60),
                product(
                    cat("주방"),
                    "모두 텀블러 500ml",
                    "하루 종일 차가운 스테인리스 텀블러",
                    24_000,
                    null,
                    "tumbler",
                    "이중 진공 스테인리스. 12시간 보냉, 6시간 보온. 식기세척기 사용 가능.",
                    groups = listOf(OptionGroupSpec("용량", listOf("500ml", "750ml"))),
                    skus = listOf(SkuSpec(mapOf("용량" to "500ml"), 0, 35), SkuSpec(mapOf("용량" to "750ml"), 4_000, 20)),
                ),
                product(cat("주방"), "모두 머그컵 세트", "두툼한 도자기 머그 2개", 18_000, 22_000, "mug", "전자레인지·식기세척기 사용 가능. 350ml 2개 세트.", stock = 45),
                product(cat("욕실"), "모두 호텔 수건 4장", "40수 코마사 호텔 타월", 32_000, null, "towel", "두께감 있는 600g 타월. 4장 세트.", stock = 30),
                product(cat("욕실"), "모두 샤워 타월", "부드러운 거품의 샤워 타월", 6_000, null, "showertowel", "촘촘한 조직으로 풍성한 거품.", stock = 80),
                product(cat("홈데코"), "모두 무드등", "따뜻한 빛의 무선 무드등", 27_000, 33_000, "moodlamp", "3단계 밝기, 터치 조작, USB-C 충전.", stock = 22),
                product(cat("홈데코"), "모두 디퓨저", "은은한 우디 향 200ml", 21_000, null, "diffuser", "리드 스틱 6개 포함. 약 2개월 사용.", stock = 28),
                product(
                    cat("가방"),
                    "모두 데일리 백팩",
                    "노트북 15인치가 들어가는 생활 방수 백팩",
                    59_000,
                    79_000,
                    "backpack",
                    "20L, 노트북 전용 수납, 생활 방수 원단.",
                    stock = 15,
                ),
                product(cat("가방"), "모두 크로스백", "가볍게 메는 미니 크로스백", 34_000, null, "crossbag", "휴대폰과 지갑이 딱 들어가는 크기.", stock = 20),
                product(
                    cat("의류"),
                    "모두 베이직 티셔츠",
                    "매일 입는 20수 코튼 티셔츠",
                    19_000,
                    null,
                    "tshirt",
                    "부드러운 20수 코튼 100%. 넉넉한 레귤러 핏.",
                    groups = listOf(OptionGroupSpec("색상", listOf("블랙", "화이트", "네이비")), OptionGroupSpec("사이즈", listOf("M", "L"))),
                    skus =
                        listOf("블랙", "화이트", "네이비").flatMap { c ->
                            listOf("M", "L").map { s ->
                                SkuSpec(
                                    mapOf("색상" to c, "사이즈" to s),
                                    if (s ==
                                        "L"
                                    ) {
                                        1_000
                                    } else {
                                        0
                                    },
                                    if (c == "네이비" && s == "L") 0 else 10,
                                )
                            }
                        },
                ),
                product(
                    cat("의류"),
                    "모두 후드 집업",
                    "기모 안감 후드 집업",
                    49_000,
                    59_000,
                    "hoodie",
                    "도톰한 기모 안감. 오버핏.",
                    groups = listOf(OptionGroupSpec("사이즈", listOf("M", "L", "XL"))),
                    skus =
                        listOf(
                            SkuSpec(mapOf("사이즈" to "M"), 0, 8),
                            SkuSpec(mapOf("사이즈" to "L"), 0, 8),
                            SkuSpec(mapOf("사이즈" to "XL"), 2_000, 5),
                        ),
                ),
                product(cat("모자"), "모두 볼캡", "무지 코튼 볼캡", 22_000, null, "cap", "조절 가능한 스트랩. 자수 로고.", stock = 33),
                product(cat("모자"), "모두 버킷햇", "여름용 린넨 버킷햇", 25_000, null, "buckethat", "통기성 좋은 린넨 혼방.", stock = 0),
                product(cat("노트·데스크"), "모두 하드커버 노트", "192쪽 도트 하드커버 노트", 12_000, null, "notebook", "100g 미색지, 실 제본으로 180도 펼침.", stock = 70),
                product(cat("노트·데스크"), "모두 젤펜 5색", "0.5mm 젤펜 5색 세트", 7_000, 9_000, "gelpen", "번짐 적은 속건 잉크.", stock = 90),
                product(cat("노트·데스크"), "모두 데스크 매트", "가죽 질감 데스크 매트 80cm", 23_000, null, "deskmat", "양면 사용, 방수 코팅.", stock = 26),
                product(cat("노트·데스크"), "모두 모니터 받침대", "서랍이 있는 원목 받침대", 38_000, 45_000, "monitorstand", "원목 상판, 수납 서랍 1개.", stock = 14),
                product(cat("노트·데스크"), "모두 다이어리 2027", "먼슬리+위클리 다이어리", 15_000, null, "diary", "2027년 1월 시작. 스티커 포함.", stock = 55),
                product(cat("노트·데스크"), "모두 스티커 팩", "다이어리 꾸미기 스티커 8장", 5_000, null, "sticker", "재접착 가능한 소재.", stock = 100),
            )
        }

        private fun product(
            category: Category?,
            name: String,
            description: String,
            price: Long,
            listPrice: Long?,
            slug: String,
            detail: String,
            stock: Int = 0,
            groups: List<OptionGroupSpec> = emptyList(),
            skus: List<SkuSpec> = listOf(SkuSpec(emptyMap(), 0, stock)),
        ): Product =
            Product.create(name = name, description = description, price = price).apply {
                updateCatalog(
                    category = category,
                    listPrice = listPrice,
                    detail = detail,
                    status = com.example.commerce.application.domain.entity.ProductStatus.SELLING,
                )
                replaceImages((1..IMAGES_PER_PRODUCT).map { "https://picsum.photos/seed/modu-$slug-$it/600/600" })
                replaceOptions(groups, skus)
            }

        private const val IMAGES_PER_PRODUCT = 2
    }
}
