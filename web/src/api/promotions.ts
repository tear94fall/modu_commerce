import { withRecentWish, type ProductSummary } from './catalog'
import { api } from './client'
import { withRecentOffers, type CouponOffer } from './coupons'
import { RecentMap } from './recent'

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

export const getPromotionBanners = () => api<PromotionBanner[]>('/api-public/v1/promotions/banners')

/** 방금 한 출석(기획전 id → 출석 결과). 레플리카가 늦어도 다시 들어오면 '오늘 출석 완료'로 보인다. */
const attended = new RecentMap<number, AttendanceResult>()

function withRecentAttendance(id: number, a: AttendanceInfo | null): AttendanceInfo | null {
  const r = attended.get(id)
  if (!a || !r || r.checkedDate !== a.today || a.checkedToday) return a
  const dates = [...new Set([...a.checkedDates, ...r.checkedDates, r.checkedDate])].sort()
  return { ...a, checkedToday: true, checkedDates: dates }
}

/** 찜 · 받은 쿠폰 · 출석은 방금 한 쓰기를 덮는다(recent.ts). */
export const getPromotion = (id: number) =>
  api<PromotionDetail>(`/api-public/v1/promotions/${id}`).then((d) => ({
    ...d,
    products: d.products.map(withRecentWish),
    coupons: d.coupons ? withRecentOffers(d.coupons) : d.coupons,
    attendance: withRecentAttendance(id, d.attendance),
  }))

/** 409 = 오늘 이미 출석, 503 = 포인트 서비스 장애(출석도 기록되지 않음). */
export const checkAttendance = (id: number) =>
  api<AttendanceResult>(`/api-public/v1/promotions/${id}/attendance`, { method: 'POST' }).then((r) => {
    attended.set(id, r)
    return r
  })

export const TYPE_LABELS: Record<PromotionType, string> = { EXHIBITION: '기획전', EVENT: '이벤트' }

export const STATUS_LABELS: Record<PromotionStatus, string> = { UPCOMING: '예정', ONGOING: '진행 중', ENDED: '종료' }

/** 배너 바탕색. 관리자가 비워 두면 브랜드 레드. */
export const BRAND_RED = '#e0243f'
export const bannerBackground = (color: string | null | undefined) => (color && /^#[0-9a-fA-F]{3,8}$/.test(color) ? color : BRAND_RED)

/** "2026-09-01" → "2026.09.01" */
export const formatDate = (date: string) => date.replaceAll('-', '.')

export const formatPeriod = (start: string, end: string) => `${formatDate(start)} ~ ${formatDate(end)}`
