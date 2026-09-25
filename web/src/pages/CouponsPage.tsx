import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { expiresOnLabel, getMyCouponCount, getMyCoupons, redeemCoupon, STATUS_LABELS, type MyCoupon, type UserCouponStatus } from '../api/coupons'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import CouponCard from '../components/CouponCard'
import { Screen, TopBar } from '../components/Layout'
import Toast from '../components/Toast'
import { formatDateTime } from '../util/format'

const TABS: UserCouponStatus[] = ['AVAILABLE', 'USED', 'EXPIRED']

const EMPTY: Record<UserCouponStatus, string> = {
  AVAILABLE: '사용할 수 있는 쿠폰이 없습니다.',
  USED: '사용한 쿠폰이 없습니다.',
  EXPIRED: '만료된 쿠폰이 없습니다.',
}

/** 쿠폰함. 사용 가능 · 사용 완료 · 만료 탭과 쿠폰 코드 등록. */
export default function CouponsPage() {
  const [tab, setTab] = useState<UserCouponStatus>('AVAILABLE')
  const [coupons, setCoupons] = useState<MyCoupon[] | null>(null)
  const [error, setError] = useState(false)
  /** 사용 가능 장수(탭 옆). null = 못 불러옴. */
  const [available, setAvailable] = useState<number | null>(null)
  const [code, setCode] = useState('')
  const [redeeming, setRedeeming] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback((status: UserCouponStatus) => {
    setError(false)
    setCoupons(null)
    getMyCoupons(status)
      .then(setCoupons)
      .catch(() => setError(true))
  }, [])
  const loadCount = useCallback(() => {
    getMyCouponCount()
      .then(setAvailable)
      .catch(() => setAvailable(null))
  }, [])
  useEffect(() => load(tab), [load, tab])
  useEffect(loadCount, [loadCount])

  const clearMessage = useCallback(() => setMessage(null), [])

  const trimmed = code.trim()
  const redeem = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!trimmed || redeeming) return
    setRedeeming(true)
    try {
      await redeemCoupon(trimmed)
      setCode('')
      setMessage('쿠폰을 받았습니다')
      loadCount()
      if (tab === 'AVAILABLE') load('AVAILABLE')
      else setTab('AVAILABLE')
    } catch (err) {
      setMessage(errorMessage(err, '쿠폰을 등록하지 못했습니다'))
    } finally {
      setRedeeming(false)
    }
  }

  return (
    <Screen>
      <TopBar title="쿠폰함" back />
      <form className="coupon-redeem" onSubmit={redeem}>
        <input
          aria-label="쿠폰 코드"
          placeholder="쿠폰 코드를 입력해 주세요"
          value={code}
          maxLength={20}
          autoCapitalize="characters"
          autoComplete="off"
          onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
        />
        <button type="submit" className="btn primary small" disabled={!trimmed || redeeming}>
          등록
        </button>
      </form>
      <Link to="/coupons" className="coupon-zone-link">
        쿠폰 받으러 가기 ›
      </Link>
      <div className="seg-tabs" role="tablist" aria-label="쿠폰 상태">
        {TABS.map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} className={tab === t ? 'on' : undefined} onClick={() => setTab(t)}>
            {STATUS_LABELS[t]}
            {t === 'AVAILABLE' && available !== null && <span className="cnt">{available}</span>}
          </button>
        ))}
      </div>
      {error ? (
        <ErrorBox message="쿠폰을 불러오지 못했습니다." onRetry={() => load(tab)} />
      ) : coupons === null ? (
        <Loading />
      ) : coupons.length === 0 ? (
        <EmptyBox message={EMPTY[tab]} />
      ) : (
        <div className="coupon-list">
          {coupons.map((c) => (
            <MyCouponCard key={c.id} coupon={c} />
          ))}
        </div>
      )}
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}

function MyCouponCard({ coupon }: { coupon: MyCoupon }) {
  const used = coupon.status === 'USED'
  return (
    <CouponCard
      coupon={coupon}
      expiry={expiresOnLabel(coupon.expiresOn)}
      dim={coupon.status !== 'AVAILABLE'}
      badge={coupon.status !== 'AVAILABLE' && <span className={`coupon-badge ${coupon.status}`}>{STATUS_LABELS[coupon.status]}</span>}
      note={used && coupon.usedAt ? `${formatDateTime(coupon.usedAt)} 사용` : undefined}
      action={
        used && coupon.orderId !== null ? (
          <Link to={`/orders/${coupon.orderId}`} className="coupon-get">
            주문 보기
          </Link>
        ) : undefined
      }
    />
  )
}
