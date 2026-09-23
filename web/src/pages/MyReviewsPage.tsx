import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { deleteReview, getMyReviews, type Review } from '../api/reviews'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import ConfirmDialog from '../components/ConfirmDialog'
import { Screen, TopBar } from '../components/Layout'
import { Stars } from '../components/Stars'
import Toast from '../components/Toast'
import { formatDateTime } from '../util/format'

/** 내가 쓴 리뷰. 숨김 처리된 리뷰는 사유와 함께 본인에게만 보인다. */
export default function MyReviewsPage() {
  const location = useLocation()
  const [reviews, setReviews] = useState<Review[] | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [page, setPage] = useState(0)
  const [error, setError] = useState(false)
  const [target, setTarget] = useState<Review | null>(null)
  const [message, setMessage] = useState<string | null>((location.state as { message?: string } | null)?.message ?? null)

  const load = useCallback((p: number) => {
    setError(false)
    getMyReviews(p)
      .then((res) => {
        setReviews((cur) => (p === 0 ? res.content : [...(cur ?? []), ...res.content]))
        setHasNext(res.number + 1 < res.totalPages)
        setPage(p)
      })
      .catch(() => setError(true))
  }, [])
  useEffect(() => load(0), [load])
  const clearMessage = useCallback(() => setMessage(null), [])

  const remove = async () => {
    const r = target
    setTarget(null)
    if (!r) return
    try {
      await deleteReview(r.id)
      setReviews((cur) => (cur ?? []).filter((x) => x.id !== r.id))
      setMessage('리뷰를 삭제했습니다')
    } catch {
      setMessage('리뷰를 삭제하지 못했습니다')
    }
  }

  return (
    <Screen>
      <TopBar title="내 리뷰" back />
      {error && reviews === null ? (
        <ErrorBox message="리뷰를 불러오지 못했습니다." onRetry={() => load(0)} />
      ) : reviews === null ? (
        <Loading />
      ) : reviews.length === 0 ? (
        <EmptyBox message="아직 쓴 리뷰가 없습니다. 주문 상세에서 구매한 상품의 리뷰를 남길 수 있어요." />
      ) : (
        <>
          <ul className="review-list mine">
            {reviews.map((r) => (
              <li key={r.id} className="review-row">
                <Link to={`/products/${r.productId}`} className="line">
                  <div className="line-thumb">{r.productImageUrl ? <img src={r.productImageUrl} alt="" /> : null}</div>
                  <div className="line-body">
                    <div className="line-name">{r.productName}</div>
                    {r.optionLabel && <div className="line-sub">{r.optionLabel}</div>}
                  </div>
                </Link>
                <div className="head">
                  <Stars rating={r.rating} />
                  <span className="when">{formatDateTime(r.createdAt)}</span>
                  <Link to={`/reviews/${r.id}/edit`} className="edit">
                    수정
                  </Link>
                  <button type="button" className="edit" onClick={() => setTarget(r)}>
                    삭제
                  </button>
                </div>
                <p className="body">{r.content}</p>
                {r.hidden && <div className="hidden-note">관리자가 숨긴 리뷰입니다{r.hiddenReason ? ` · ${r.hiddenReason}` : ''}</div>}
              </li>
            ))}
          </ul>
          {hasNext && (
            <button type="button" className="btn outline small load-more" onClick={() => load(page + 1)}>
              더 보기
            </button>
          )}
        </>
      )}
      <ConfirmDialog open={target !== null} title="리뷰 삭제" message="리뷰를 삭제할까요? 되돌릴 수 없습니다." confirmLabel="삭제" onConfirm={remove} onClose={() => setTarget(null)} />
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
