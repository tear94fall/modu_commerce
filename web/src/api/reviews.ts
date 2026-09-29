import type { Page } from './catalog'
import { api, ApiError } from './client'
import { CountHint, insertMissing, RecentMap } from './recent'

export interface Review {
  id: number
  productId: number
  productName: string
  optionLabel: string
  productImageUrl: string | null
  orderItemId: number
  /** 남의 리뷰는 null. */
  userId: string | null
  /** 남의 리뷰는 가려진 이름(임*섭), 내 리뷰는 그대로. 이름을 모르면 '모두 회원'. */
  authorName: string
  authorEmail: string | null
  rating: number
  content: string
  hidden: boolean
  hiddenReason: string | null
  mine: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface RatingCount {
  rating: number
  count: number
}

/** distribution 은 5점부터 1점까지 항상 다섯 줄. */
export interface ReviewSummary {
  count: number
  average: number
  distribution: RatingCount[]
}

/** 리뷰 쓰기 화면이 먼저 묻는 것: 이 주문 줄에 쓸 수 있나, 이미 썼다면 어느 리뷰인가. */
export interface ReviewTarget {
  orderItemId: number
  productId: number
  productName: string
  optionLabel: string
  imageUrl: string | null
  reviewable: boolean
  reviewId: number | null
}

export type ReviewSort = 'latest' | 'high' | 'low'
export const REVIEW_SORTS: ReviewSort[] = ['latest', 'high', 'low']
export const REVIEW_SORT_LABELS: Record<ReviewSort, string> = { latest: '최신순', high: '별점 높은순', low: '별점 낮은순' }

export const REVIEW_MIN = 10
export const REVIEW_MAX = 1000

/** 목록 덮개가 서버 값에 없던 내 쓰기를 반영하며 바꾼 별점(요약·상품 평점도 같이 맞춘다). null = 없음(추가·삭제). */
export interface RatingDelta {
  from: number | null
  to: number | null
}

/** 상품 리뷰 한 장. [ratingDeltas] 는 레플리카가 아직 모르는 내 쓰기(요약·평점에 더할 것). */
export interface ReviewPage extends Page<Review> {
  ratingDeltas?: RatingDelta[]
}

// ---- 방금 한 리뷰 쓰기(복제 지연 덮개, recent.ts) ----

/** review = null 이면 지웠다. created = 이 창 안에서 새로 썼다(목록에 없으면 끼운다). */
interface ReviewWrite {
  productId: number | null
  review: Review | null
  created: boolean
}

const reviewWrites = new RecentMap<number, ReviewWrite>()
/** 주문 줄 → 리뷰 id(null = 지워서 다시 쓸 수 있음). 주문 상세의 '리뷰 쓰기/내 리뷰'와 쓰기 화면의 중복 확인에 쓴다. */
const itemReviews = new RecentMap<number, number | null>()
/** 마이 탭 '리뷰 N'. */
const myReviewTotal = new CountHint()

const SORT_BEFORE: Record<ReviewSort, (a: Review, b: Review) => boolean> = {
  latest: (a, b) => a.id > b.id,
  high: (a, b) => a.rating > b.rating || (a.rating === b.rating && a.id > b.id),
  low: (a, b) => a.rating < b.rating || (a.rating === b.rating && a.id > b.id),
}

/** 서버 한 장에 내 쓰기를 덮는다: 지운 것은 빼고, 고친 것은 바꾸고, 새로 쓴 것이 빠졌으면 제자리에 끼운다. */
function overlayReviews(page: Page<Review>, sort: ReviewSort, belongs: (w: ReviewWrite) => boolean, visibleOnly: boolean) {
  const deltas: RatingDelta[] = []
  let removed = 0
  const content: Review[] = []
  for (const r of page.content) {
    const w = reviewWrites.get(r.id)
    if (!w) {
      content.push(r)
    } else if (w.review === null || (visibleOnly && w.review.hidden)) {
      removed++
      if (!r.hidden) deltas.push({ from: r.rating, to: null })
    } else {
      content.push(w.review)
      if (!r.hidden && r.rating !== w.review.rating) deltas.push({ from: r.rating, to: w.review.rating })
    }
  }
  const created = reviewWrites
    .entries()
    .reverse()
    .map(([, w]) => w)
    .filter((w) => w.created && w.review !== null && !(visibleOnly && w.review.hidden) && belongs(w))
    .map((w) => w.review as Review)
  const merged = insertMissing({ ...page, content }, created, SORT_BEFORE[sort], (r) => r.id)
  const added = merged.content.filter((r) => !page.content.some((x) => x.id === r.id))
  for (const r of added) if (!r.hidden) deltas.push({ from: null, to: r.rating })
  return { content: merged.content, totalElements: Math.max(0, page.totalElements + merged.inserted - removed), deltas }
}

function remember(id: number, orderItemId: number | null, w: ReviewWrite) {
  reviewWrites.set(id, w)
  if (orderItemId !== null) itemReviews.set(orderItemId, w.review === null ? null : id)
}

export const getProductReviews = (productId: number, page = 0, sort: ReviewSort = 'latest', size = 10): Promise<ReviewPage> =>
  api<Page<Review>>(`/api-public/v1/products/${productId}/reviews?page=${page}&size=${size}&sort=${sort}`).then((res) => {
    const o = overlayReviews(res, sort, (w) => w.productId === productId, true)
    return { ...res, content: o.content, totalElements: o.totalElements, ratingDeltas: o.deltas }
  })
export const getReviewSummary = (productId: number) => api<ReviewSummary>(`/api-public/v1/products/${productId}/reviews/summary`)
/** 이미 쓴(또는 방금 지운) 줄이면 레플리카가 늦어도 그 결과로 답한다 — 쓰기 화면이 중복 등록 대신 수정으로 간다. */
export const getReviewTarget = (orderItemId: number) =>
  api<ReviewTarget>(`/api-public/v1/reviews/targets/${orderItemId}`).then((t) => {
    const recent = itemReviews.get(orderItemId)
    if (recent === undefined) return t
    return recent === null ? { ...t, reviewId: null, reviewable: t.reviewable || t.reviewId !== null } : { ...t, reviewId: recent, reviewable: false }
  })
export const getMyReviews = (page = 0, size = 20) =>
  api<Page<Review>>(`/api-public/v1/me/reviews?page=${page}&size=${size}`).then((res) => {
    const o = overlayReviews(res, 'latest', () => true, false)
    return { ...res, content: o.content, totalElements: myReviewTotal.observe(o.totalElements) }
  })
/** 방금 쓰거나 고친 리뷰는 그 응답을, 방금 지운 리뷰는 404 를 돌려준다(레플리카에는 없거나 옛 내용일 수 있다). */
export const getMyReview = (id: number): Promise<Review> => {
  const w = reviewWrites.get(id)
  if (w) return w.review ? Promise.resolve(w.review) : Promise.reject(new ApiError(404, ''))
  return api<Review>(`/api-public/v1/reviews/${id}`)
}
export const writeReview = (orderItemId: number, rating: number, content: string) =>
  api<Review>('/api-public/v1/reviews', { method: 'POST', body: JSON.stringify({ orderItemId, rating, content }) }).then((r) => {
    remember(r.id, r.orderItemId ?? orderItemId, { productId: r.productId, review: r, created: true })
    myReviewTotal.write(1)
    return r
  })
export const editReview = (id: number, rating: number, content: string) =>
  api<Review>(`/api-public/v1/reviews/${id}`, { method: 'PUT', body: JSON.stringify({ rating, content }) }).then((r) => {
    remember(r.id, r.orderItemId, { productId: r.productId, review: r, created: reviewWrites.get(id)?.created ?? false })
    return r
  })
/** [known] 은 지우는 리뷰(주문 줄을 알면 주문 상세가 바로 '리뷰 쓰기'로 돌아간다). */
export const deleteReview = (id: number, known?: Pick<Review, 'productId' | 'orderItemId'>) =>
  api<void>(`/api-public/v1/reviews/${id}`, { method: 'DELETE' }).then(() => {
    const before = reviewWrites.get(id)?.review
    remember(id, known?.orderItemId ?? before?.orderItemId ?? null, { productId: known?.productId ?? before?.productId ?? null, review: null, created: false })
    myReviewTotal.write(-1)
  })

/** 주문 상세의 줄마다 방금 쓴·지운 리뷰를 반영한다(레플리카는 아직 '리뷰 쓰기'를 보일 수 있다). */
export function withRecentReviewState<T extends { id: number; reviewId: number | null; reviewable: boolean }>(item: T, cancelled: boolean): T {
  const recent = itemReviews.get(item.id)
  if (recent === undefined) return item
  return recent === null ? { ...item, reviewId: null, reviewable: !cancelled } : { ...item, reviewId: recent, reviewable: false }
}

/** 요약(평균·분포)에 레플리카가 아직 모르는 내 쓰기를 더한다. 같은 때 받은 목록의 [ReviewPage.ratingDeltas] 를 준다. */
export function applyRatingDeltas(summary: ReviewSummary, deltas: RatingDelta[] | undefined): ReviewSummary {
  if (!deltas || deltas.length === 0) return summary
  const distribution = summary.distribution.map((d) => {
    let count = d.count
    for (const x of deltas) {
      if (x.from === d.rating) count--
      if (x.to === d.rating) count++
    }
    return { ...d, count: Math.max(0, count) }
  })
  const count = distribution.reduce((n, d) => n + d.count, 0)
  const sum = distribution.reduce((n, d) => n + d.rating * d.count, 0)
  return { count, average: count === 0 ? 0 : Math.round((sum / count) * 10) / 10, distribution }
}

/** 상품 상세·카드의 리뷰 수·평균에 같은 보정을 한다(분포가 없으니 평균×수로 되짚는다). */
export function applyRatingDeltasTo<T extends { reviewCount: number; ratingAverage: number }>(p: T, deltas: RatingDelta[] | undefined): T {
  if (!deltas || deltas.length === 0) return p
  let count = p.reviewCount
  let sum = p.ratingAverage * p.reviewCount
  for (const x of deltas) {
    if (x.from !== null) {
      count--
      sum -= x.from
    }
    if (x.to !== null) {
      count++
      sum += x.to
    }
  }
  count = Math.max(0, count)
  return { ...p, reviewCount: count, ratingAverage: count === 0 ? 0 : Math.round((sum / count) * 10) / 10 }
}

/** 4.5 → "4.5", 4 → "4.0". 카드와 요약이 같은 표기를 쓴다. */
export const formatRating = (average: number) => average.toFixed(1)
