import { formatPrice } from '../util/format'

interface PriceProps {
  price: number
  listPrice?: number | null
  discountRate?: number
}

/** 할인 중이면 "30% 12,000원 ~~18,000원~~", 아니면 가격만. */
export default function Price({ price, listPrice, discountRate = 0 }: PriceProps) {
  const discounted = discountRate > 0 && listPrice != null && listPrice > price
  return (
    <div className="price">
      {discounted && <span className="rate">{discountRate}%</span>}
      <span className="now">{formatPrice(price)}</span>
      {discounted && <span className="was">{formatPrice(listPrice)}</span>}
    </div>
  )
}
