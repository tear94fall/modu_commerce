import type { RollingTier, TierSummary } from '../api/customer'
import { tierProgress } from '../util/tier'

/** 등급 이름 알약. 바탕은 등급 색. */
export function TierBadge({ tier }: { tier: TierSummary }) {
  return (
    <span className="tier-badge" style={{ background: tier.color }}>
      {tier.name}
    </span>
  )
}

/** 진행 막대. 색은 다음(또는 최고) 등급 색. */
export function TierProgressBar({ rolling }: { rolling: RollingTier }) {
  const pct = Math.round(tierProgress(rolling) * 100)
  const target = rolling.nextTier ?? rolling.expectedTier
  return (
    <div className="tier-bar" role="progressbar" aria-label={rolling.nextTier ? `${rolling.nextTier.name}까지` : '최고 등급'} aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct}>
      <span style={{ width: `${pct}%`, background: target.color }} />
    </div>
  )
}
