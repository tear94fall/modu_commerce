import { describe, expect, it } from 'vitest'
import { isAllowedPushPath } from './web'

describe('isAllowedPushPath', () => {
  it('allows home, coupons, product and promotion paths only', () => {
    for (const p of ['/', '/coupons', '/products/1', '/promotions/123']) expect(isAllowedPushPath(p)).toBe(true)
    for (const p of ['', '/orders', '/products/', '/products/1/reviews', '/products/abc', '//evil.com', 'https://x/', '/coupons?x=1', null]) expect(isAllowedPushPath(p)).toBe(false)
  })
})
