import type { ReactNode } from 'react'
import { discountLabel, minOrderLabel, type CouponTerms } from '../api/coupons'

interface CouponCardProps {
  coupon: CouponTerms
  /** "~ 2026.10.31" 또는 "받은 날부터 7일". */
  expiry: string
  /** 이름 옆 작은 딱지(사용 완료 · 만료 등). */
  badge?: ReactNode
  /** 오른쪽 칸(받기 버튼, 할인 금액 등). 없으면 오른쪽 칸을 그리지 않는다. */
  action?: ReactNode
  /** 흐리게(쓸 수 없는 쿠폰). */
  dim?: boolean
  /** 맨 아래 한 줄(쓸 수 없는 까닭, 사용한 날 등). */
  note?: ReactNode
}

/** 티켓 모양 쿠폰. 왼쪽에 할인 · 이름 · 조건, 점선 너머 오른쪽에 동작. */
export default function CouponCard({ coupon, expiry, badge, action, dim = false, note }: CouponCardProps) {
  const minOrder = minOrderLabel(coupon)
  return (
    <div className={`coupon ${dim ? 'dim' : ''}`.trim()} data-testid="coupon-card">
      <div className="coupon-body">
        <div className="coupon-discount">{discountLabel(coupon)}</div>
        <div className="coupon-name">
          <span>{coupon.name}</span>
          {badge}
        </div>
        <div className="coupon-cond">{[coupon.scopeLabel, minOrder].filter(Boolean).join(' · ')}</div>
        <div className="coupon-expiry">{expiry}</div>
        {note && <div className="coupon-note">{note}</div>}
      </div>
      {action !== undefined && <div className="coupon-side">{action}</div>}
    </div>
  )
}
