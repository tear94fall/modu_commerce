package com.example.commerce.application.usecase.result

import com.example.commerce.application.domain.entity.AttendanceCheck
import com.example.commerce.application.domain.entity.Product
import com.example.commerce.application.domain.entity.ProductStatus
import com.example.commerce.application.domain.entity.Promotion
import com.example.commerce.application.domain.entity.PromotionStatus
import com.example.commerce.application.domain.entity.PromotionType
import java.time.LocalDate
import java.time.LocalDateTime

/** 앱 홈 배너 한 장. */
data class PromotionBannerResult(
    val id: Long,
    val type: PromotionType,
    val title: String,
    val subtitle: String?,
    val bannerImageUrl: String?,
    val bannerColor: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    companion object {
        fun from(p: Promotion) =
            PromotionBannerResult(
                requireNotNull(p.id),
                p.type,
                p.title,
                p.subtitle,
                p.bannerImageUrl,
                p.bannerColor,
                p.startDate,
                p.endDate,
            )
    }
}

/** 앱 상세. 기획전이면 [products], 이벤트면 [attendance]. */
data class PromotionDetailResult(
    val id: Long,
    val type: PromotionType,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val bannerImageUrl: String?,
    val bannerColor: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: PromotionStatus,
    val products: List<ProductSummaryResult>,
    val attendance: AttendanceInfoResult?,
)

data class AttendanceInfoResult(
    val rewardPoints: Long?,
    val today: LocalDate,
    val checkedToday: Boolean,
    val checkedDates: List<LocalDate>,
    val totalDays: Int,
)

data class AttendanceResult(
    val checkedDate: LocalDate,
    val rewardPoints: Long,
    val rewardMessage: String?,
    val checkedDates: List<LocalDate>,
)

/** 백오피스 목록 한 줄. */
data class AdminPromotionSummaryResult(
    val id: Long,
    val type: PromotionType,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: PromotionStatus,
    val visible: Boolean,
    val sortOrder: Int,
    val productCount: Int,
    val attendanceCount: Long,
    val bannerImageUrl: String?,
    val bannerColor: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            p: Promotion,
            today: LocalDate,
            attendanceCount: Long,
        ) = AdminPromotionSummaryResult(
            requireNotNull(p.id),
            p.type,
            p.title,
            p.startDate,
            p.endDate,
            p.statusOn(today),
            p.visible,
            p.sortOrder,
            p.products.size,
            attendanceCount,
            p.bannerImageUrl,
            p.bannerColor,
            p.createdAt,
            p.updatedAt,
        )
    }
}

data class AdminPromotionProductResult(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val price: Long,
    val listPrice: Long?,
    val status: ProductStatus,
) {
    companion object {
        fun from(p: Product) = AdminPromotionProductResult(requireNotNull(p.id), p.name, p.imageUrl, p.price, p.listPrice, p.status)
    }
}

data class AdminPromotionDetailResult(
    val id: Long,
    val type: PromotionType,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: PromotionStatus,
    val visible: Boolean,
    val sortOrder: Int,
    val productCount: Int,
    val attendanceCount: Long,
    val bannerImageUrl: String?,
    val bannerColor: String?,
    val pointRuleCode: String?,
    val rewardPoints: Long?,
    val products: List<AdminPromotionProductResult>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(
            p: Promotion,
            today: LocalDate,
            attendanceCount: Long,
            products: List<Product>,
        ) = AdminPromotionDetailResult(
            requireNotNull(p.id),
            p.type,
            p.title,
            p.subtitle,
            p.description,
            p.startDate,
            p.endDate,
            p.statusOn(today),
            p.visible,
            p.sortOrder,
            products.size,
            attendanceCount,
            p.bannerImageUrl,
            p.bannerColor,
            p.pointRuleCode,
            p.rewardPoints,
            products.map(AdminPromotionProductResult::from),
            p.createdAt,
            p.updatedAt,
        )
    }
}

data class AdminAttendanceResult(
    val userId: String,
    val checkDate: LocalDate,
    val rewardPoints: Long,
    val rewardMessage: String?,
    val createdAt: LocalDateTime?,
) {
    companion object {
        fun from(a: AttendanceCheck) = AdminAttendanceResult(a.userId, a.checkDate, a.rewardPoints, a.rewardMessage, a.createdAt)
    }
}
