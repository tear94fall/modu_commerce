import type { CustomerMe, Tier, TierSummary } from '../api/customer'

export const WELCOME: TierSummary = { code: 'WELCOME', name: '웰컴', color: '#64748B', earnRate: 1, minAmount: 0 }
export const SILVER: TierSummary = { code: 'SILVER', name: '실버', color: '#94A3B8', earnRate: 2, minAmount: 100000 }
export const GOLD: TierSummary = { code: 'GOLD', name: '골드', color: '#D97706', earnRate: 3, minAmount: 300000 }
export const VIP: TierSummary = { code: 'VIP', name: 'VIP', color: '#7C3AED', earnRate: 5, minAmount: 700000 }

export const tiers = (): Tier[] => [
  { ...WELCOME, monthlyCoupons: [] },
  { ...SILVER, monthlyCoupons: [{ name: '실버 3천원', discountLabel: '3,000원' }] },
  { ...GOLD, monthlyCoupons: [{ name: '골드 5%', discountLabel: '5%' }] },
  { ...VIP, monthlyCoupons: [{ name: 'VIP 1만원', discountLabel: '10,000원' }, { name: 'VIP 무료배송', discountLabel: null }] },
]

/** 골드 고객, 최근 6개월 184,000원(다음 달 실버 예상) → VIP까지는 아니고 골드까지 116,000원. */
export const customerMe = (over: Partial<CustomerMe> = {}): CustomerMe => ({
  userId: 'u1',
  joinedAt: '2026-09-01T00:00:00',
  termsAgreedAt: '2026-09-01T00:00:00',
  privacyAgreedAt: '2026-09-01T00:00:00',
  termsVersion: '2026-10',
  tier: GOLD,
  basisAmount: 320000,
  rolling: { amount: 184000, expectedTier: SILVER, nextTier: GOLD, amountToNext: 116000 },
  periodLabel: '2026.03 ~ 2026.08',
  ...over,
})
