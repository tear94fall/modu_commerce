import type { ProductSummary } from './catalog'
import type { CouponOffer } from './coupons'
import { api } from './client'

/** 기획전(상품 묶음) · 이벤트(출석 체크 또는 쿠폰). */
export type PromotionType = 'EXHIBITION' | 'EVENT'

/** 이벤트 종류. 기획전은 null. 예전 이벤트는 null 이어도 attendance 가 있으면 출석 이벤트. */
export type EventKind = 'ATTENDANCE' | 'COUPON'

/** 서버가 한국 날짜 기준으로 정한다. 예정 · 진행 중 · 종료. */
export type PromotionStatus = 'UPCOMING' | 'ONGOING' | 'ENDED'

/** 홈 배너. 진행 중이고 보이는 것만, 관리자 순서대로 온다. 날짜는 "YYYY-MM-DD"(한국 날짜). */
export interface PromotionBanner {
  id: number
  type: PromotionType
  title: string
  subtitle: string | null
  bannerImageUrl: string | null
  /** "#E11D48" 같은 CSS 색. 없으면 브랜드 레드. */
  bannerColor: string | null
  startDate: string
  endDate: string
}

export interface AttendanceInfo {
  /** 한 번 출석할 때 적립되는 포인트(표시용). 없으면 보상 없음. */
  rewardPoints: number | null
  /** 서버 기준 오늘(한국 날짜). */
  today: string
  checkedToday: boolean
  /** 이 이벤트에서 내가 출석한 날, 오름차순. */
  checkedDates: string[]
  totalDays: number
}

export interface PromotionDetail extends PromotionBanner {
  description: string | null
  status: PromotionStatus
  /** 기획전 상품(판매 중인 것만, 관리자 순서). 이벤트는 빈 목록. */
  products: ProductSummary[]
  eventKind: EventKind | null
  /** 기획전: 함께 붙은 쿠폰(없을 수 있음). 쿠폰 이벤트: 이벤트 쿠폰. */
  coupons: CouponOffer[]
  /** 출석 이벤트만. */
  attendance: AttendanceInfo | null
}

export interface AttendanceResult {
  checkedDate: string
  /** 실제로 적립된 포인트. 보상이 없거나 한도를 넘으면 0. */
  rewardPoints: number
  /** 0 인 까닭(예: "오늘 적립 한도를 넘었습니다"). */
  rewardMessage: string | null
  checkedDates: string[]
}

export const getPromotionBanners = () => api<PromotionBanner[]>('/api/v1/promotions/banners')

export const getPromotion = (id: number) => api<PromotionDetail>(`/api/v1/promotions/${id}`)

/** 409 = 오늘 이미 출석, 503 = 포인트 서비스 장애(출석도 기록되지 않음). */
export const checkAttendance = (id: number) => api<AttendanceResult>(`/api/v1/promotions/${id}/attendance`, { method: 'POST' })

export const TYPE_LABELS: Record<PromotionType, string> = { EXHIBITION: '기획전', EVENT: '이벤트' }

export const STATUS_LABELS: Record<PromotionStatus, string> = { UPCOMING: '예정', ONGOING: '진행 중', ENDED: '종료' }

/** 배너 바탕색. 관리자가 비워 두면 브랜드 레드. */
export const BRAND_RED = '#e0243f'
export const bannerBackground = (color: string | null | undefined) => (color && /^#[0-9a-fA-F]{3,8}$/.test(color) ? color : BRAND_RED)

/** "2026-09-01" → "2026.09.01" */
export const formatDate = (date: string) => date.replaceAll('-', '.')

export const formatPeriod = (start: string, end: string) => `${formatDate(start)} ~ ${formatDate(end)}`
