import type { RollingTier } from '../api/customer'
import { formatPrice } from './format'

const isTop = (rolling: RollingTier) => !rolling.nextTier || rolling.amountToNext === null

/** 다음 등급까지의 진행률(0~1). 최고 등급이면 1. */
export function tierProgress(rolling: RollingTier): number {
  if (isTop(rolling)) return 1
  const goal = rolling.amount + (rolling.amountToNext ?? 0)
  return goal <= 0 ? 1 : Math.min(1, Math.max(0, rolling.amount / goal))
}

/** "최근 6개월 구매 184,000원 · VIP까지 516,000원" / "… · 최고 등급이에요" */
export function tierProgressText(rolling: RollingTier): string {
  const head = `최근 6개월 구매 ${formatPrice(rolling.amount)}`
  if (isTop(rolling)) return `${head} · 최고 등급이에요`
  return `${head} · ${rolling.nextTier!.name}까지 ${formatPrice(rolling.amountToNext!)}`
}
