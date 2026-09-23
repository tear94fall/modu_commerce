import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { deleteReview, editReview, getMyReview, getReviewTarget, REVIEW_MAX, REVIEW_MIN, writeReview } from '../api/reviews'
import { ErrorBox, Loading } from '../components/Boxes'
import ConfirmDialog from '../components/ConfirmDialog'
import { Screen, TopBar } from '../components/Layout'
import { StarPicker } from '../components/Stars'
import Toast from '../components/Toast'

interface Target {
  productId: number
  productName: string
  optionLabel: string
  imageUrl: string | null
  orderItemId: number
}

/**
 * 리뷰 쓰기(`/reviews/new?orderItemId=`)와 수정(`/reviews/:id/edit`)을 한 화면이 맡는다.
 * 쓰기는 주문 줄이 내 것이고 아직 리뷰가 없어야 하고, 이미 썼다면 수정 화면으로 보낸다.
 */
export default function ReviewFormPage() {
  const { id } = useParams()
  const reviewId = id ? Number(id) : null
  const [params] = useSearchParams()
  const orderItemId = Number(params.get('orderItemId'))
  const navigate = useNavigate()
  const [target, setTarget] = useState<Target | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'notFound' | 'blocked' | 'error'>('loading')
  const [rating, setRating] = useState(0)
  const [content, setContent] = useState('')
  const [working, setWorking] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    setStatus('loading')
    const load =
      reviewId !== null
        ? getMyReview(reviewId).then((r) => {
            setTarget({ productId: r.productId, productName: r.productName, optionLabel: r.optionLabel, imageUrl: r.productImageUrl, orderItemId: r.orderItemId })
            setRating(r.rating)
            setContent(r.content)
            setStatus('ok')
          })
        : getReviewTarget(orderItemId).then((t) => {
            if (t.reviewId !== null) {
              navigate(`/reviews/${t.reviewId}/edit`, { replace: true })
              return
            }
            setTarget({ productId: t.productId, productName: t.productName, optionLabel: t.optionLabel, imageUrl: t.imageUrl, orderItemId: t.orderItemId })
            setStatus(t.reviewable ? 'ok' : 'blocked')
          })
    load.catch((e: unknown) => setStatus(e instanceof ApiError && e.status === 404 ? 'notFound' : 'error'))
  }, [reviewId, orderItemId, navigate])

  const clearMessage = useCallback(() => setMessage(null), [])
  const trimmed = content.trim()
  const valid = rating > 0 && trimmed.length >= REVIEW_MIN && trimmed.length <= REVIEW_MAX

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!valid || working || !target) return
    setWorking(true)
    try {
      if (reviewId !== null) {
        await editReview(reviewId, rating, trimmed)
        navigate(-1)
      } else {
        await writeReview(target.orderItemId, rating, trimmed)
        navigate(`/products/${target.productId}/reviews`, { replace: true, state: { message: '리뷰를 등록했습니다' } })
      }
    } catch (err) {
      setMessage(err instanceof ApiError && err.status === 400 ? err.message : '리뷰를 저장하지 못했습니다')
      setWorking(false)
    }
  }

  const remove = async () => {
    setConfirm(false)
    if (reviewId === null || working) return
    setWorking(true)
    try {
      await deleteReview(reviewId)
      navigate('/my/reviews', { replace: true, state: { message: '리뷰를 삭제했습니다' } })
    } catch {
      setMessage('리뷰를 삭제하지 못했습니다')
      setWorking(false)
    }
  }

  const title = reviewId !== null ? '리뷰 수정' : '리뷰 쓰기'
  if (status !== 'ok' || !target) {
    return (
      <Screen>
        <TopBar title={title} back />
        {status === 'loading' ? (
          <Loading />
        ) : status === 'notFound' ? (
          <ErrorBox message="리뷰를 쓸 주문 상품을 찾을 수 없습니다." />
        ) : status === 'blocked' ? (
          <ErrorBox message="취소된 주문의 상품에는 리뷰를 쓸 수 없습니다." />
        ) : (
          <ErrorBox message="불러오지 못했습니다." />
        )}
      </Screen>
    )
  }

  return (
    <Screen className="with-bottom-bar">
      <TopBar title={title} back />
      <form className="review-form" onSubmit={submit}>
        <div className="line">
          <div className="line-thumb">{target.imageUrl ? <img src={target.imageUrl} alt="" /> : null}</div>
          <div className="line-body">
            <div className="line-name">{target.productName}</div>
            {target.optionLabel && <div className="line-sub">{target.optionLabel}</div>}
          </div>
        </div>
        <div className="field">
          <div className="label">상품은 어떠셨나요?</div>
          <StarPicker value={rating} onChange={setRating} />
        </div>
        <div className="field">
          <label htmlFor="review-content" className="label">
            솔직한 리뷰를 남겨 주세요
          </label>
          <textarea
            id="review-content"
            value={content}
            maxLength={REVIEW_MAX}
            placeholder={`${REVIEW_MIN}자 이상 써 주세요. 다른 분들의 구매에 도움이 됩니다.`}
            onChange={(e) => setContent(e.target.value)}
          />
          <div className={`counter ${trimmed.length > 0 && trimmed.length < REVIEW_MIN ? 'warn' : ''}`}>
            {trimmed.length} / {REVIEW_MAX}
          </div>
        </div>
        {reviewId !== null && (
          <button type="button" className="text-danger" onClick={() => setConfirm(true)}>
            리뷰 삭제
          </button>
        )}
        <div className="bottom-bar">
          <button type="submit" className="btn primary" disabled={!valid || working}>
            {reviewId !== null ? '수정 완료' : '등록'}
          </button>
        </div>
      </form>
      <ConfirmDialog open={confirm} title="리뷰 삭제" message="리뷰를 삭제할까요? 되돌릴 수 없습니다." confirmLabel="삭제" onConfirm={remove} onClose={() => setConfirm(false)} />
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
