import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { formatPrice } from '../util/format'

export interface LineView {
  key: string | number
  productId: number
  productName: string
  optionLabel: string
  imageUrl: string | null
  unitPrice: number
  quantity: number
  lineAmount: number
  /** 줄 아래 오른쪽에 붙는 것(주문 상세의 '리뷰 쓰기'). */
  action?: ReactNode
}

/** 주문서·주문 상세의 상품 줄. 사진, 이름, 옵션, 단가 × 수량, 줄 금액. */
export default function OrderItems({ lines }: { lines: LineView[] }) {
  return (
    <div>
      {lines.map((l) => (
        <div key={l.key} className="line">
          <Link to={`/products/${l.productId}`} className="line-thumb">
            {l.imageUrl ? <img src={l.imageUrl} alt="" /> : null}
          </Link>
          <div className="line-body">
            <div className="line-name">{l.productName}</div>
            {l.optionLabel && <div className="line-sub">{l.optionLabel}</div>}
            <div className="line-sub">
              {formatPrice(l.unitPrice)} × {l.quantity}
            </div>
          </div>
          <div className="line-amount">{formatPrice(l.lineAmount)}</div>
          {l.action && <div className="line-action">{l.action}</div>}
        </div>
      ))}
    </div>
  )
}
