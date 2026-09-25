import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getCart } from '../api/cart'
import { CartIcon } from './Icons'

/** 상단바 장바구니 아이콘 + 수량 뱃지. 화면에 들어올 때 한 번 센다. */
export default function CartButton() {
  const [count, setCount] = useState(0)
  useEffect(() => {
    getCart()
      .then((c) => setCount(c.itemCount))
      .catch(() => {})
  }, [])
  return (
    <Link to="/cart" className="icon-btn" aria-label="장바구니">
      <CartIcon />
      {count > 0 && <span className="badge">{count > 99 ? '99+' : count}</span>}
    </Link>
  )
}
