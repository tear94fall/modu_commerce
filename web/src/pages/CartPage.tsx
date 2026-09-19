import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { changeCartQuantity, getCart, orderable, removeCartItem, type Cart } from '../api/cart'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import Toast from '../components/Toast'
import { formatPrice } from '../util/format'

/** 장바구니. 수량 ±, 삭제, 주문하기(주문 가능한 줄만). 품절·판매중지 줄은 이유를 보여 주고 주문에서 뺀다. */
export default function CartPage() {
  const [cart, setCart] = useState<Cart | null>(null)
  const [error, setError] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const navigate = useNavigate()

  const load = useCallback(() => {
    getCart()
      .then((c) => {
        setCart(c)
        setError(false)
      })
      .catch(() => setError(true))
  }, [])
  useEffect(load, [load])

  const change = async (id: number, quantity: number) => {
    if (!cart) return
    const before = cart
    setCart({ ...cart, items: cart.items.map((i) => (i.id === id ? { ...i, quantity, lineAmount: i.unitPrice * quantity } : i)) })
    try {
      await changeCartQuantity(id, quantity)
      load()
    } catch {
      setCart(before)
      setMessage('수량을 바꾸지 못했습니다')
    }
  }

  const remove = async (id: number) => {
    if (!cart) return
    const before = cart
    setCart({ ...cart, items: cart.items.filter((i) => i.id !== id) })
    try {
      await removeCartItem(id)
      load()
    } catch {
      setCart(before)
      setMessage('삭제하지 못했습니다')
    }
  }

  const clearMessage = useCallback(() => setMessage(null), [])
  const orderables = cart?.items.filter(orderable) ?? []
  const total = orderables.reduce((sum, i) => sum + i.lineAmount, 0)
  const count = orderables.reduce((sum, i) => sum + i.quantity, 0)

  return (
    <Screen className={cart && cart.items.length > 0 ? 'with-bottom-bar' : ''}>
      <TopBar title="장바구니" back />
      {error ? (
        <ErrorBox message="장바구니를 불러오지 못했습니다." onRetry={load} />
      ) : cart === null ? (
        <Loading />
      ) : cart.items.length === 0 ? (
        <EmptyBox message="장바구니가 비었습니다." />
      ) : (
        <>
          {cart.items.map((item) => (
            <div key={item.id} className="cart-row">
              <Link to={`/products/${item.productId}`} className="thumb">
                {item.imageUrl ? <img src={item.imageUrl} alt="" /> : null}
              </Link>
              <div className="body">
                <div className="name">{item.productName}</div>
                {item.optionLabel && <div className="sub">{item.optionLabel}</div>}
                {!item.available ? (
                  <div className="warn">판매 중지</div>
                ) : item.stock < item.quantity ? (
                  <div className="warn">재고 부족 (남은 수량 {item.stock})</div>
                ) : null}
                <div className="controls">
                  <div className="stepper">
                    <button type="button" aria-label="수량 줄이기" disabled={item.quantity <= 1} onClick={() => change(item.id, item.quantity - 1)}>
                      −
                    </button>
                    <span aria-label="수량">{item.quantity}</span>
                    <button type="button" aria-label="수량 늘리기" disabled={!item.available || item.quantity >= item.stock} onClick={() => change(item.id, item.quantity + 1)}>
                      +
                    </button>
                  </div>
                  <div className="amount">{formatPrice(item.lineAmount)}</div>
                </div>
                <div className="links">
                  <button type="button" onClick={() => remove(item.id)}>
                    삭제
                  </button>
                  <Link to={`/products/${item.productId}`}>
                    <button type="button">상품 보기</button>
                  </Link>
                </div>
              </div>
            </div>
          ))}
          <div className="bottom-bar">
            <div className="summary">
              <div className="label">총 {count}개</div>
              <div className="amount">{formatPrice(total)}</div>
            </div>
            <button type="button" className="btn primary cta" disabled={orderables.length === 0} onClick={() => navigate(`/checkout?cartItemIds=${orderables.map((i) => i.id).join(',')}`)}>
              주문하기
            </button>
          </div>
        </>
      )}
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
