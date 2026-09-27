import { api, ApiError, isCustomerRequired } from './client'

/** 회원 등급 요약. earnRate 는 퍼센트(3 = 구매 금액의 3%). */
export interface TierSummary {
  code: string
  name: string
  /** #RRGGBB */
  color: string
  earnRate: number
  /** 이 등급의 기준 금액(최근 6개월 배송 완료 금액 하한). 가장 낮은 등급은 0. */
  minAmount: number
}

export interface MonthlyCoupon {
  name: string
  discountLabel: string | null
}

/** GET /api/v1/tiers 한 줄. 낮은 등급부터. */
export interface Tier extends TierSummary {
  monthlyCoupons: MonthlyCoupon[]
}

export interface RollingTier {
  /** 지금까지의 최근 6개월(5달 전 1일 ~ 지금, KST) 배송 완료 금액. */
  amount: number
  /** 이 금액이면 다음 달에 될 등급. */
  expectedTier: TierSummary
  /** 그다음 등급. 최고 등급이면 null. */
  nextTier: TierSummary | null
  /** 다음 등급까지 남은 금액. 최고 등급이면 null. */
  amountToNext: number | null
}

/** 커머스 고객(약관 동의 완료). 시각은 UTC 이고 시간대 표시가 없다. */
export interface CustomerMe {
  userId: string
  joinedAt: string
  termsAgreedAt: string | null
  privacyAgreedAt: string | null
  termsVersion: string | null
  tier: TierSummary
  /** 지금 등급을 정한 금액. */
  basisAmount: number
  rolling: RollingTier
  /** 지금 등급의 기준 기간. "2026.04 ~ 2026.09" */
  periodLabel: string
}

export interface JoinCustomerInput {
  agreeTerms: boolean
  agreePrivacy: boolean
  /** true 면 서버가 혜택·이벤트 알림 수신 동의까지 저장한다. */
  marketing?: boolean
}

/** 내 커머스 고객 정보. 가입(약관 동의) 전이면 null(서버 404/403 CUSTOMER_REQUIRED). */
export const getMyCustomer = () =>
  api<CustomerMe>('/api/v1/me/customer', { quiet: true }).catch((e: unknown) => {
    if ((e instanceof ApiError && e.status === 404) || isCustomerRequired(e)) return null
    throw e
  })

export const joinCustomer = (input: JoinCustomerInput) => api<CustomerMe>('/api/v1/me/customer', { method: 'POST', body: JSON.stringify(input) })

export const getTiers = () => api<Tier[]>('/api/v1/tiers')

/** 구매 적립 예상 포인트 = floor(결제 금액 × 적립률 / 100). */
export const earnPoints = (paymentAmount: number, earnRate: number) => Math.max(0, Math.floor((paymentAmount * earnRate) / 100))
