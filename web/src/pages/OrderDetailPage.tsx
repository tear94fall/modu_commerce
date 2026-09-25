import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { cancelOrder, fullAddress, getOrder, paymentLabel, statusLabel, type OrderDetail } from '../api/orders'
import { ErrorBox, Loading } from '../components/Boxes'
import ConfirmDialog from '../components/ConfirmDialog'
import { Screen, TopBar } from '../components/Layout'
import OrderItems from '../components/OrderItems'
import Toast from '../components/Toast'
import { formatDateTime, formatPrice } from '../util/format'

export default function OrderDetailPage() {
  const { id } = useParams()
  const orderId = Number(id)
  const location = useLocation()
  const [order, setOrder] = useState<OrderDetail | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'notFound' | 'error'>('loading')
  const [confirm, setConfirm] = useState(false)
  const [working, setWorking] = useState(false)
  const [message, setMessage] = useState<string | null>((location.state as { justOrdered?: boolean } | null)?.justOrdered ? '주문이 완료됐습니다' : null)

  const load = useCallback(() => {
    setStatus('loading')
    getOrder(orderId)
      .then((o) => {
        setOrder(o)
        setStatus('ok')
      })
      .catch((e: unknown) => setStatus(e instanceof ApiError && e.status === 404 ? 'notFound' : 'error'))
  }, [orderId])
  useEffect(load, [load])

  const clearMessage = useCallback(() => setMessage(null), [])

  const cancel = async () => {
    setConfirm(false)
    if (working) return
    setWorking(true)
    try {
      setOrder(await cancelOrder(orderId))
      setMessage('주문을 취소했습니다')
    } catch (e) {
      setMessage(e instanceof ApiError && e.status === 400 ? e.message : '주문을 취소하지 못했습니다')
    } finally {
      setWorking(false)
    }
  }

  if (status !== 'ok' || !order) {
    return (
      <Screen>
        <TopBar title="주문 상세" back />
        {status === 'notFound' ? <ErrorBox message="주문을 찾을 수 없습니다." /> : status === 'error' ? <ErrorBox message="주문을 불러오지 못했습니다." onRetry={load} /> : <Loading />}
      </Screen>
    )
  }

  const cancellable = order.status === 'PAID'
  return (
    <Screen className={cancellable ? 'with-bottom-bar' : ''}>
      <TopBar title="주문 상세" back />
      <div className="order-head">
        <div className="no">주문번호 {order.orderNo}</div>
        <div className="row">
          <span className={`status ${order.status}`}>{statusLabel(order.status)}</span>
          <span className="date">{formatDateTime(order.paidAt)}</span>
        </div>
        {order.cancelledAt && <div className="cancelled">취소 {formatDateTime(order.cancelledAt)}</div>}
      </div>
      <section className="block">
        <div className="block-head">
          <h2>주문 상품</h2>
        </div>
        <OrderItems
          lines={order.items.map((i) => ({
            ...i,
            key: i.id,
            action:
              i.reviewId !== null ? (
                <Link to={`/reviews/${i.reviewId}/edit`} className="btn outline small">
                  내 리뷰
                </Link>
              ) : i.reviewable ? (
                <Link to={`/reviews/new?orderItemId=${i.id}`} className="btn outline small">
                  리뷰 쓰기
                </Link>
              ) : null,
          }))}
        />
      </section>
      <section className="block">
        <div className="block-head">
          <h2>받는 사람</h2>
        </div>
        <div className="addr">
          <div className="addr-name">
            {order.recipient} · {order.phone}
          </div>
          <div className="addr-line">
            ({order.zipCode}) {fullAddress(order)}
          </div>
        </div>
      </section>
      <section className="block">
        <div className="block-head">
          <h2>결제</h2>
        </div>
        <div className="kv muted">
          <span>상품 금액</span>
          <span>{formatPrice(order.totalAmount)}</span>
        </div>
        {order.couponDiscount > 0 && (
          <div className="kv muted">
            <span>
              쿠폰 할인{order.couponName ? ` (${order.couponName})` : ''}
              {order.status === 'CANCELLED' ? ' · 쿠폰 반환됨' : ''}
            </span>
            <span>-{formatPrice(order.couponDiscount)}</span>
          </div>
        )}
        {order.pointAmount > 0 && (
          <div className="kv muted">
            <span>포인트 사용{order.status === 'CANCELLED' ? ' (환불됨)' : ''}</span>
            <span>-{formatPrice(order.pointAmount)}</span>
          </div>
        )}
        <div className="kv">
          <span>{paymentLabel(order.paymentMethod)}</span>
          <span className="v">{formatPrice(order.paymentAmount)}</span>
        </div>
      </section>
      {cancellable && (
        <div className="bottom-bar">
          <button type="button" className="btn outline" disabled={working} onClick={() => setConfirm(true)}>
            주문 취소
          </button>
        </div>
      )}
      <ConfirmDialog open={confirm} title="주문 취소" message="주문을 취소할까요? 결제 금액은 즉시 환불(모의)됩니다." confirmLabel="주문 취소" onConfirm={cancel} onClose={() => setConfirm(false)} />
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
