import type { ApplicableCoupon, CouponOffer, MyCoupon } from '../api/coupons'

/** 테스트용 쿠폰 모양들. */
export const offer = (over: Partial<CouponOffer> = {}): CouponOffer => ({
  couponId: 1,
  name: '가을 맞이 3천원',
  description: null,
  discountType: 'FIXED',
  discountValue: 3000,
  maxDiscount: null,
  minOrderAmount: 0,
  scopeLabel: '전체 상품',
  expiryLabel: '2026.10.31까지',
  downloaded: false,
  soldOut: false,
  ...over,
})

export const myCoupon = (over: Partial<MyCoupon> = {}): MyCoupon => ({
  id: 31,
  couponId: 1,
  name: '가을 맞이 3천원',
  description: null,
  discountType: 'FIXED',
  discountValue: 3000,
  maxDiscount: null,
  minOrderAmount: 0,
  scopeLabel: '전체 상품',
  expiresOn: '2026-10-31',
  status: 'AVAILABLE',
  source: 'DOWNLOAD',
  issuedAt: '2026-09-25T01:00:00',
  usedAt: null,
  orderId: null,
  ...over,
})

export const applicable = (over: Partial<ApplicableCoupon> = {}): ApplicableCoupon => ({
  ...myCoupon(),
  discount: 3000,
  applicable: true,
  reason: null,
  ...over,
})
