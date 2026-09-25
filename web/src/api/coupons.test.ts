import { describe, expect, it } from 'vitest'
import { discountLabel, expiresOnLabel, minOrderLabel, offerDiscountFor } from './coupons'

describe('coupons', () => {
  it('labels fixed and percent discounts', () => {
    expect(discountLabel({ discountType: 'FIXED', discountValue: 3000, maxDiscount: null })).toBe('3,000원 할인')
    expect(discountLabel({ discountType: 'PERCENT', discountValue: 10, maxDiscount: 5000 })).toBe('10% 할인 (최대 5,000원)')
    expect(discountLabel({ discountType: 'PERCENT', discountValue: 15, maxDiscount: null })).toBe('15% 할인')
  })

  it('labels the minimum order and expiry', () => {
    expect(minOrderLabel({ minOrderAmount: 30000 })).toBe('30,000원 이상 구매 시')
    expect(minOrderLabel({ minOrderAmount: 0 })).toBeNull()
    expect(expiresOnLabel('2026-10-31')).toBe('~ 2026.10.31')
  })

  it('estimates the discount on one product', () => {
    expect(offerDiscountFor({ discountType: 'FIXED', discountValue: 3000, maxDiscount: null }, 9000)).toBe(3000)
    expect(offerDiscountFor({ discountType: 'PERCENT', discountValue: 10, maxDiscount: 5000 }, 90000)).toBe(5000)
    expect(offerDiscountFor({ discountType: 'PERCENT', discountValue: 10, maxDiscount: 5000 }, 12345)).toBe(1234)
  })
})
