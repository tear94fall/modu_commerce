import type { Page } from './catalog'
import { api } from './client'

export interface Review {
  id: number
  productId: number
  productName: string
  optionLabel: string
  productImageUrl: string | null
  orderItemId: number
  /** 남의 리뷰는 null. */
  userId: string | null
  /** 남의 리뷰는 가려진 이름(임*섭), 내 리뷰는 그대로. 이름을 모르면 '모두 회원'. */
  authorName: string
  authorEmail: string | null
  rating: number
  content: string
  hidden: boolean
  hiddenReason: string | null
  mine: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface RatingCount {
  rating: number
  count: number
}

/** distribution 은 5점부터 1점까지 항상 다섯 줄. */
export interface ReviewSummary {
  count: number
  average: number
  distribution: RatingCount[]
}

/** 리뷰 쓰기 화면이 먼저 묻는 것: 이 주문 줄에 쓸 수 있나, 이미 썼다면 어느 리뷰인가. */
export interface ReviewTarget {
  orderItemId: number
  productId: number
  productName: string
  optionLabel: string
  imageUrl: string | null
  reviewable: boolean
  reviewId: number | null
}

export type ReviewSort = 'latest' | 'high' | 'low'
export const REVIEW_SORTS: ReviewSort[] = ['latest', 'high', 'low']
export const REVIEW_SORT_LABELS: Record<ReviewSort, string> = { latest: '최신순', high: '별점 높은순', low: '별점 낮은순' }

export const REVIEW_MIN = 10
export const REVIEW_MAX = 1000

export const getProductReviews = (productId: number, page = 0, sort: ReviewSort = 'latest', size = 10) =>
  api<Page<Review>>(`/api/v1/products/${productId}/reviews?page=${page}&size=${size}&sort=${sort}`)
export const getReviewSummary = (productId: number) => api<ReviewSummary>(`/api/v1/products/${productId}/reviews/summary`)
export const getReviewTarget = (orderItemId: number) => api<ReviewTarget>(`/api/v1/reviews/targets/${orderItemId}`)
export const getMyReviews = (page = 0, size = 20) => api<Page<Review>>(`/api/v1/me/reviews?page=${page}&size=${size}`)
export const getMyReview = (id: number) => api<Review>(`/api/v1/reviews/${id}`)
export const writeReview = (orderItemId: number, rating: number, content: string) =>
  api<Review>('/api/v1/reviews', { method: 'POST', body: JSON.stringify({ orderItemId, rating, content }) })
export const editReview = (id: number, rating: number, content: string) => api<Review>(`/api/v1/reviews/${id}`, { method: 'PUT', body: JSON.stringify({ rating, content }) })
export const deleteReview = (id: number) => api<void>(`/api/v1/reviews/${id}`, { method: 'DELETE' })

/** 4.5 → "4.5", 4 → "4.0". 카드와 요약이 같은 표기를 쓴다. */
export const formatRating = (average: number) => average.toFixed(1)
