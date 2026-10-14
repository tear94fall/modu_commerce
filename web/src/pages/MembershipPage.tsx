import { useCallback, useEffect, useState } from 'react'
import { getTiers, type Tier } from '../api/customer'
import { ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import { TierBadge, TierProgressBar } from '../components/Tier'
import { useCustomer } from '../customer/context'
import { formatPrice } from '../util/format'
import { tierProgressText } from '../util/tier'

const minAmountLabel = (amount: number) => (amount <= 0 ? '가입하면 바로' : `${formatPrice(amount)} 이상`)

const couponsLabel = (tier: Tier) =>
  tier.monthlyCoupons.length === 0 ? '없음' : tier.monthlyCoupons.map((c) => (c.discountLabel ? `${c.name} (${c.discountLabel})` : c.name)).join(', ')

/** 마이 > 회원 등급. 등급표와 내 등급, 다음 달 예상 등급. */
export default function MembershipPage() {
  const { customer, refresh } = useCustomer()
  const [tiers, setTiers] = useState<Tier[] | null>(null)
  const [error, setError] = useState(false)

  const load = useCallback(() => {
    setError(false)
    getTiers()
      .then(setTiers)
      .catch(() => setError(true))
  }, [])
  useEffect(load, [load])
  useEffect(() => {
    refresh()
  }, [refresh])

  const current = customer?.tier.code
  const expected = customer?.rolling.expectedTier

  return (
    <Screen className="my membership">
      <TopBar title="회원 등급" back />
      {customer && (
        <section className="my-card tier-summary" aria-label="내 등급">
          <div className="now">
            <span className="lbl">지금 내 등급</span>
            <TierBadge tier={customer.tier} />
            <span className="rate">구매 금액의 {customer.tier.earnRate}% 적립</span>
          </div>
          <div className="basis">
            {customer.periodLabel} 배송 완료 {formatPrice(customer.basisAmount)} 기준
          </div>
          <div className="expected">
            다음 달 예상 등급 <b style={{ color: customer.rolling.expectedTier.color }}>{customer.rolling.expectedTier.name}</b>
          </div>
          <div className="progress-text">{tierProgressText(customer.rolling)}</div>
          <TierProgressBar rolling={customer.rolling} />
        </section>
      )}

      {error && tiers === null ? (
        <ErrorBox message="등급 정보를 불러오지 못했습니다." onRetry={load} />
      ) : tiers === null ? (
        <Loading />
      ) : (
        <ul className="tier-list" aria-label="등급별 혜택">
          {tiers.map((t) => {
            const isCurrent = t.code === current
            return (
              <li key={t.code} className={`my-card tier-card${isCurrent ? ' current' : ''}`} aria-label={t.name} aria-current={isCurrent ? 'true' : undefined} style={{ borderColor: isCurrent ? t.color : undefined }}>
                <div className="head">
                  <span className="dot" style={{ background: t.color }} aria-hidden="true" />
                  <span className="nm">{t.name}</span>
                  {isCurrent && <span className="chip mine">내 등급</span>}
                  {!isCurrent && t.code === expected?.code && <span className="chip next">다음 달 예상</span>}
                </div>
                <dl>
                  <div>
                    <dt>기준 금액</dt>
                    <dd>{minAmountLabel(t.minAmount)}</dd>
                  </div>
                  <div>
                    <dt>적립률</dt>
                    <dd>{t.earnRate}%</dd>
                  </div>
                  <div>
                    <dt>매월 쿠폰</dt>
                    <dd>{couponsLabel(t)}</dd>
                  </div>
                </dl>
              </li>
            )
          })}
        </ul>
      )}

      <p className="tier-rule">매월 1일, 지난 6개월 배송 완료 금액으로 정해져요. 포인트로 낸 금액은 빠져요.</p>
    </Screen>
  )
}
