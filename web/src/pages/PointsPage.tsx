import { useCallback, useEffect, useState } from 'react'
import { formatPointDateTime, formatPoints, formatSignedPoints, getMyPoints, getPointHistory, transactionTitle, typeLabel, type PointTransaction } from '../api/points'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'

/** 내 포인트. 잔액과 최근 순 적립·사용 내역. */
export default function PointsPage() {
  const [balance, setBalance] = useState<number | null>(null)
  const [items, setItems] = useState<PointTransaction[] | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [page, setPage] = useState(0)
  const [error, setError] = useState(false)

  const load = useCallback((p: number) => {
    setError(false)
    const history = getPointHistory(p).then((res) => {
      setItems((cur) => (p === 0 ? res.content : [...(cur ?? []), ...res.content]))
      setHasNext(res.number + 1 < res.totalPages)
      setPage(p)
    })
    const summary = p === 0 ? getMyPoints().then(setBalance) : Promise.resolve()
    Promise.all([history, summary]).catch(() => setError(true))
  }, [])
  useEffect(() => load(0), [load])

  return (
    <Screen>
      <TopBar title="포인트" back />
      {error && items === null ? (
        <ErrorBox message="포인트를 불러오지 못했습니다." onRetry={() => load(0)} />
      ) : items === null || balance === null ? (
        <Loading />
      ) : (
        <>
          <section className="point-head">
            <div className="label">사용 가능 포인트</div>
            <div className="balance">{formatPoints(balance)}</div>
          </section>
          {items.length === 0 ? (
            <EmptyBox message="아직 적립·사용 내역이 없습니다." />
          ) : (
            <ul className="point-list">
              {items.map((t) => (
                <li key={t.id} className="point-row">
                  <div className="body">
                    <div className="title">{transactionTitle(t)}</div>
                    <div className="sub">
                      {formatPointDateTime(t.createdDate)} · {typeLabel(t.type)}
                    </div>
                  </div>
                  <div className="side">
                    <div className={`delta ${t.amount < 0 ? 'minus' : 'plus'}`}>{formatSignedPoints(t.amount)}</div>
                    <div className="after">{formatPoints(t.balanceAfter)}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
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
