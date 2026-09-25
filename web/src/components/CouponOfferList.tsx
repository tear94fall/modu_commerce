import { useState } from 'react'
import { ApiError, errorMessage } from '../api/client'
import { downloadCoupon, type CouponOffer } from '../api/coupons'
import CouponCard from './CouponCard'

interface CouponOfferListProps {
  offers: CouponOffer[]
  /** 받은 뒤(또는 이미 받았다고 409 가 오면) 그 쿠폰을 downloaded 로 바꿔 알린다. */
  onChange: (next: CouponOffer[]) => void
  onMessage: (message: string) => void
}

/** 받을 수 있는 쿠폰 목록. 한 장씩 "받기", 받은 것은 "받음", 다 나간 것은 "소진". */
export default function CouponOfferList({ offers, onChange, onMessage }: CouponOfferListProps) {
  const [working, setWorking] = useState<number | null>(null)

  const mark = (couponId: number, patch: Partial<CouponOffer>) => onChange(offers.map((o) => (o.couponId === couponId ? { ...o, ...patch } : o)))

  const download = async (offer: CouponOffer) => {
    if (working !== null || offer.downloaded || offer.soldOut) return
    setWorking(offer.couponId)
    try {
      await downloadCoupon(offer.couponId)
      mark(offer.couponId, { downloaded: true })
      onMessage('쿠폰을 받았습니다')
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) mark(offer.couponId, { downloaded: true })
      onMessage(errorMessage(e, '쿠폰을 받지 못했습니다'))
    } finally {
      setWorking(null)
    }
  }

  return (
    <div className="coupon-list">
      {offers.map((o) => (
        <CouponCard
          key={o.couponId}
          coupon={o}
          expiry={o.expiryLabel}
          dim={o.soldOut && !o.downloaded}
          action={
            <button
              type="button"
              className="coupon-get"
              aria-label={o.downloaded ? `${o.name} 받음` : o.soldOut ? `${o.name} 소진` : `${o.name} 받기`}
              disabled={o.downloaded || o.soldOut || working !== null}
              onClick={() => download(o)}
            >
              {o.downloaded ? '받음' : o.soldOut ? '소진' : '받기'}
            </button>
          }
        />
      ))}
    </div>
  )
}
