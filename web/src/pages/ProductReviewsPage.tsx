import { useCallback, useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { formatRating, getProductReviews, getReviewSummary, REVIEW_SORT_LABELS, REVIEW_SORTS, type Review, type ReviewSort, type ReviewSummary } from '../api/reviews'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import ReviewList from '../components/ReviewList'
import { Stars } from '../components/Stars'
import Toast from '../components/Toast'

/** 상품 리뷰 전체. 요약(평균·분포) + 정렬 + 목록. */
export default function ProductReviewsPage() {
  const { id } = useParams()
  const productId = Number(id)
  const location = useLocation()
  const [summary, setSummary] = useState<ReviewSummary | null>(null)
  const [reviews, setReviews] = useState<Review[] | null>(null)
  const [sort, setSort] = useState<ReviewSort>('latest')
  const [hasNext, setHasNext] = useState(false)
  const [page, setPage] = useState(0)
  const [error, setError] = useState(false)
  const [message, setMessage] = useState<string | null>((location.state as { message?: string } | null)?.message ?? null)

  const load = useCallback(
    (p: number, s: ReviewSort) => {
      setError(false)
      const list = getProductReviews(productId, p, s).then((res) => {
        setReviews((cur) => (p === 0 ? res.content : [...(cur ?? []), ...res.content]))
        setHasNext(res.number + 1 < res.totalPages)
        setPage(p)
      })
      const head = p === 0 && s === 'latest' ? getReviewSummary(productId).then(setSummary) : Promise.resolve()
      Promise.all([list, head]).catch(() => setError(true))
    },
    [productId],
  )
  useEffect(() => load(0, 'latest'), [load])

  const changeSort = (s: ReviewSort) => {
    setSort(s)
    setReviews(null)
    load(0, s)
  }
  const clearMessage = useCallback(() => setMessage(null), [])

  return (
    <Screen>
      <TopBar title="리뷰" back />
      {error && reviews === null ? (
        <ErrorBox message="리뷰를 불러오지 못했습니다." onRetry={() => load(0, sort)} />
      ) : summary === null ? (
        <Loading />
      ) : (
        <>
          <section className="review-summary">
            <div className="avg">
              <div className="num">{formatRating(summary.average)}</div>
              <Stars rating={summary.average} />
              <div className="cnt">리뷰 {summary.count.toLocaleString('ko-KR')}개</div>
            </div>
            <ul className="dist" aria-label="별점 분포">
              {summary.distribution.map((d) => (
                <li key={d.rating}>
                  <span className="lbl">{d.rating}점</span>
                  <span className="bar">
                    <i style={{ width: summary.count === 0 ? 0 : `${(d.count / summary.count) * 100}%` }} />
                  </span>
                  <span className="n">{d.count}</span>
                </li>
              ))}
            </ul>
          </section>
          <div className="chips" role="radiogroup" aria-label="정렬">
            {REVIEW_SORTS.map((s) => (
              <button key={s} type="button" role="radio" aria-checked={sort === s} className={`chip ${sort === s ? 'active' : ''}`} onClick={() => changeSort(s)}>
                {REVIEW_SORT_LABELS[s]}
              </button>
            ))}
          </div>
          {reviews === null ? (
            <Loading />
          ) : reviews.length === 0 ? (
            <EmptyBox message="아직 리뷰가 없습니다. 구매하신 상품이라면 주문 상세에서 첫 리뷰를 남겨 주세요." />
          ) : (
            <ReviewList reviews={reviews} />
          )}
          {hasNext && (
            <button type="button" className="btn outline small load-more" onClick={() => load(page + 1, sort)}>
              더 보기
            </button>
          )}
          <div className="review-foot">
            <Link to={`/products/${productId}`}>상품으로 돌아가기</Link>
          </div>
        </>
      )}
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
