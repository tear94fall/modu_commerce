import { formatPrice } from '../util/format'
import { api } from './client'
import type { OrderLine } from './orders'
import { clearWithRecentWrites, RecentMap } from './recent'

/** FIXED: discountValue 원 할인. PERCENT: discountValue % 할인(maxDiscount 까지). */
export type DiscountType = 'FIXED' | 'PERCENT'

export type UserCouponStatus = 'AVAILABLE' | 'USED' | 'EXPIRED'

/** 받은 경로. 쿠폰존 받기 · 코드 등록 · 관리자 지급 · 쿠폰 이벤트. */
export type CouponSource = 'DOWNLOAD' | 'CODE' | 'ADMIN' | 'EVENT'

/** 할인 표기에 필요한 것만. 받을 수 있는 쿠폰과 내 쿠폰 모두 이 모양을 갖는다. */
export interface CouponTerms {
  name: string
  description: string | null
  discountType: DiscountType
  discountValue: number
  maxDiscount: number | null
  /** 상품 금액 합이 이 이상일 때만 쓸 수 있다. 0 이면 조건 없음. */
  minOrderAmount: number
  /** "전체 상품" | "'문구' 카테고리" | "지정 상품 3개" */
  scopeLabel: string
}

/** 받을 수 있는 쿠폰(쿠폰존 · 상품 상세 · 기획전 · 쿠폰 이벤트). */
export interface CouponOffer extends CouponTerms {
  couponId: number
  /** "2026.10.31까지" | "받은 날부터 7일" */
  expiryLabel: string
  /** 이미 받았다(상태와 상관없이). 쿠폰은 한 사람이 한 번만 받는다. */
  downloaded: boolean
  soldOut: boolean
}

/** 내 쿠폰. id 가 주문에 쓰는 userCouponId. */
export interface MyCoupon extends CouponTerms {
  id: number
  couponId: number
  /** 한국 날짜 "YYYY-MM-DD", 이 날까지 쓸 수 있다. */
  expiresOn: string
  status: UserCouponStatus
  source: CouponSource
  /** UTC, 시간대 표시 없음. */
  issuedAt: string
  usedAt: string | null
  orderId: number | null
}

/** 주문서에서 고르는 내 쿠폰. 쓸 수 있는 것이 할인 큰 순으로 먼저 온다. */
export interface ApplicableCoupon extends MyCoupon {
  /** 이 주문에 적용했을 때 할인 금액. */
  discount: number
  applicable: boolean
  /** 쓸 수 없는 까닭("5,000원 이상 주문 시 사용 가능"). */
  reason: string | null
}

export interface EventClaimResult {
  issued: MyCoupon[]
  alreadyHad: number
}

// ---- 방금 받은·쓴 쿠폰(복제 지연 덮개, recent.ts) ----

/** 받은 내 쿠폰(쿠폰존 받기 · 코드 등록 · 쿠폰 이벤트). 레플리카가 늦어도 쿠폰함·개수·"받음"에 바로 보인다. */
const issuedCoupons = new RecentMap<number, MyCoupon>()
/** 주문에 쓴 내 쿠폰 id → 주문 id. 쿠폰함 '사용 가능'에서 빠지고 주문서에 다시 뜨지 않는다. */
const usedCoupons = new RecentMap<number, number>()

/** 최근에 본 내 쿠폰 모양(주문서 쿠폰 목록 · 쿠폰함). 방금 쓴 쿠폰을 '사용 완료'에 끼울 때 쓴다. */
const seenCoupons = new Map<number, MyCoupon>()
clearWithRecentWrites(seenCoupons)
const see = <T extends MyCoupon>(list: T[]) => {
  for (const c of list) seenCoupons.set(c.id, c)
  return list
}

const rememberIssued = (c: MyCoupon) => {
  issuedCoupons.set(c.id, c)
  return c
}

/** 주문이 쿠폰을 썼다(createOrder 가 부른다). */
export function rememberCouponUsed(userCouponId: number, orderId: number) {
  usedCoupons.set(userCouponId, orderId)
}

/** 주문을 취소해 쿠폰이 돌아왔다(cancelOrder 가 부른다). */
export function forgetCouponsOfOrder(orderId: number) {
  for (const [id, o] of usedCoupons.entries()) if (o === orderId) usedCoupons.delete(id)
}

function withRecentCoupons(list: MyCoupon[], status: UserCouponStatus): MyCoupon[] {
  const used = (c: MyCoupon): MyCoupon => {
    const orderId = usedCoupons.get(c.id)
    return orderId === undefined ? c : { ...c, status: 'USED', orderId, usedAt: c.usedAt ?? new Date().toISOString().slice(0, 19) }
  }
  const out = list.map(used).filter((c) => c.status === status)
  const have = new Set(out.map((c) => c.id))
  const recent = [...issuedCoupons.entries().map(([, c]) => c), ...usedCoupons.entries().flatMap(([id]) => seenCoupons.get(id) ?? [])]
  for (const c of recent) {
    const now = used(c)
    if (now.status === status && !have.has(c.id)) {
      out.push(now)
      have.add(c.id)
    }
  }
  // 서버 순서(내 쿠폰 id 최신 순)를 지킨다.
  return out.length === list.length && out.every((c, i) => c.id === list[i].id) ? out : out.sort((a, b) => b.id - a.id)
}

/** 받은 쿠폰이면 downloaded 로 바꾼다(쿠폰존 · 상품 상세 · 기획전 · 쿠폰 이벤트). */
export function withRecentOffers(offers: CouponOffer[]): CouponOffer[] {
  const got = new Set(issuedCoupons.entries().map(([, c]) => c.couponId))
  return got.size === 0 ? offers : offers.map((o) => (!o.downloaded && got.has(o.couponId) ? { ...o, downloaded: true } : o))
}

export const getMyCoupons = (status: UserCouponStatus = 'AVAILABLE') =>
  api<MyCoupon[]>(`/api-public/v1/me/coupons?status=${status}`).then((list) => withRecentCoupons(see(list), status))

/**
 * 사용 가능 장수. 사용 가능 목록(덮개 적용)을 센다 — 서버의 개수(/me/coupons/count)도 같은 목록을 세지만 레플리카에서 세므로
 * 방금 받은 쿠폰이 빠질 수 있고, 무엇이 빠졌는지 알 수 없어 고칠 수 없다. 한 사람의 쿠폰 몇 장이라 목록으로 세도 가볍다.
 */
export const getMyCouponCount = () => getMyCoupons('AVAILABLE').then((list) => list.length)

/** productId 를 주면 그 상품에 쓸 수 있는 것만. */
export const getDownloadableCoupons = (productId?: number) =>
  api<CouponOffer[]>(`/api-public/v1/coupons/downloadable${productId ? `?productId=${productId}` : ''}`).then(withRecentOffers)

/** 409 = 이미 받음, 400 = 받을 수 없음(기간 · 소진). */
export const downloadCoupon = (couponId: number) => api<MyCoupon>(`/api-public/v1/coupons/${couponId}/download`, { method: 'POST' }).then(rememberIssued)

/** 404 = 없는 코드, 409 = 이미 받음, 400 = 기간 · 소진. */
export const redeemCoupon = (code: string) =>
  api<MyCoupon>('/api-public/v1/coupons/redeem', { method: 'POST', body: JSON.stringify({ code }) }).then(rememberIssued)

/** 방금 주문에 쓴 쿠폰은 뺀다(레플리카는 아직 사용 가능으로 줄 수 있다). */
export const getApplicableCoupons = (items: OrderLine[]) =>
  api<ApplicableCoupon[]>('/api-public/v1/coupons/applicable', { method: 'POST', body: JSON.stringify({ items }) }).then((list) =>
    see(list).filter((c) => !usedCoupons.has(c.id)),
  )

/** 쿠폰 이벤트의 쿠폰을 한 번에 받는다. 모두 이미 받았으면 409. */
export const claimEventCoupons = (promotionId: number) =>
  api<EventClaimResult>(`/api-public/v1/promotions/${promotionId}/coupons`, { method: 'POST' }).then((r) => {
    r.issued.forEach(rememberIssued)
    return r
  })

/** "3,000원 할인" / "10% 할인 (최대 5,000원)" */
export function discountLabel(c: Pick<CouponTerms, 'discountType' | 'discountValue' | 'maxDiscount'>): string {
  if (c.discountType === 'FIXED') return `${formatPrice(c.discountValue)} 할인`
  return c.maxDiscount ? `${c.discountValue}% 할인 (최대 ${formatPrice(c.maxDiscount)})` : `${c.discountValue}% 할인`
}

/** "30,000원 이상 구매 시". 조건이 없으면 null. */
export const minOrderLabel = (c: Pick<CouponTerms, 'minOrderAmount'>) => (c.minOrderAmount > 0 ? `${formatPrice(c.minOrderAmount)} 이상 구매 시` : null)

/** "2026-10-31" → "~ 2026.10.31" */
export const expiresOnLabel = (date: string) => `~ ${date.replaceAll('-', '.')}`

export const STATUS_LABELS: Record<UserCouponStatus, string> = { AVAILABLE: '사용 가능', USED: '사용 완료', EXPIRED: '만료' }

/**
 * 이 가격의 상품 하나에 받을 수 있는 가장 큰 할인(상품 상세의 "최대 5,000원 할인 쿠폰").
 * 정률은 상품 가격에 적용하고 최대 할인으로 자른다. 최소 주문 금액은 수량에 따라 달라지므로 보지 않는다.
 */
export function offerDiscountFor(c: Pick<CouponTerms, 'discountType' | 'discountValue' | 'maxDiscount'>, price: number): number {
  if (c.discountType === 'FIXED') return c.discountValue
  const percent = Math.floor((price * c.discountValue) / 100)
  return c.maxDiscount ? Math.min(percent, c.maxDiscount) : percent
}
