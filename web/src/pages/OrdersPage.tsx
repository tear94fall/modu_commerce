import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getOrders, statusLabel, type OrderSummary } from '../api/orders'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import { formatDateTime, formatPrice } from '../util/format'

export default function OrdersPage() {
  const [orders, setOrders] = useState<OrderSummary[] | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [page, setPage] = useState(0)
  const [error, setError] = useState(false)

  const load = useCallback((p: number) => {
    setError(false)
    getOrders(p)
      .then((res) => {
        setOrders((cur) => (p === 0 ? res.content : [...(cur ?? []), ...res.content]))
        setHasNext(res.number + 1 < res.totalPages)
        setPage(p)
      })
      .catch(() => setError(true))
  }, [])
  useEffect(() => load(0), [load])

  return (
    <Screen>
      <TopBar title="주문 내역" back />
      {error && orders === null ? (
        <ErrorBox message="주문 내역을 불러오지 못했습니다." onRetry={() => load(0)} />
      ) : orders === null ? (
        <Loading />
      ) : orders.length === 0 ? (
        <EmptyBox message="주문 내역이 없습니다." />
      ) : (
        <>
          {orders.map((o) => (
            <Link key={o.id} to={`/orders/${o.id}`} className="order-card">
              <div className="head">
                <span>{formatDateTime(o.createdAt)}</span>
                <span className={`status ${o.status}`}>{statusLabel(o.status)}</span>
              </div>
              <div className="row">
                <div className="thumb">{o.firstImageUrl ? <img src={o.firstImageUrl} alt="" /> : null}</div>
                <div>
                  <div className="title">{o.itemCount > 1 ? `${o.firstItemName} 외 ${o.itemCount - 1}건` : o.firstItemName}</div>
                  <div className="amount">{formatPrice(o.totalAmount)}</div>
                  <div className="no">{o.orderNo}</div>
                </div>
              </div>
            </Link>
          ))}
          {hasNext && (
            <button type="button" className="btn outline small load-more" onClick={() => load(page + 1)}>
              더 보기
            </button>
          )}
        </>
      )}
    </Screen>
  )
}
