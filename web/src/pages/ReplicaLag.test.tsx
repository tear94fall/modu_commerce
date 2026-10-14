import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getProduct, getWishlist, setWish, type ProductDetail, type ProductSummary } from '../api/catalog'
import { createOrder, type OrderDetail } from '../api/orders'
import { checkAttendance, getPromotion, type PromotionDetail } from '../api/promotions'
import { markNotificationRead, type NotificationItem } from '../api/push'
import { clearRecentWrites, RECENT_MS } from '../api/recent'
import { deleteReview, getMyReviews, getReviewTarget, writeReview, type Review, type ReviewSummary } from '../api/reviews'
import NotificationButton from '../components/NotificationButton'
import { myCoupon } from '../test-fixtures/coupons'
import { fakeServer, page } from '../test-fixtures/server'
import CouponsPage from './CouponsPage'
import MyReviewsPage from './MyReviewsPage'
import NotificationsPage from './NotificationsPage'
import OrderDetailPage from './OrderDetailPage'
import ProductReviewsPage from './ProductReviewsPage'
import ReviewFormPage from './ReviewFormPage'

/**
 * 레플리카가 늦는(쓰기 뒤에도 옛 값을 주는) 서버에서 쓰고 바로 다시 읽는 흐름.
 * fetch 만 가짜로 두고 api/*.ts 의 덮개(recent.ts)는 그대로 돈다.
 */

const review = (over: Partial<Review> = {}): Review => ({
  id: 70,
  productId: 5,
  productName: '모두 머그컵 세트',
  optionLabel: '',
  productImageUrl: null,
  orderItemId: 1,
  userId: 'u1',
  authorName: '임준섭',
  authorEmail: null,
  rating: 5,
  content: '튼튼하고 색이 예뻐요 추천합니다',
  hidden: false,
  hiddenReason: null,
  mine: true,
  createdAt: '2026-09-29T01:00:00',
  updatedAt: null,
  ...over,
})

const emptySummary: ReviewSummary = { count: 0, average: 0, distribution: [5, 4, 3, 2, 1].map((rating) => ({ rating, count: 0 })) }

const order = (over: Partial<OrderDetail> = {}): OrderDetail => ({
  id: 77,
  orderNo: '20260929-ABC123',
  status: 'DELIVERED',
  totalAmount: 10200,
  couponDiscount: 0,
  couponName: null,
  pointAmount: 0,
  paymentAmount: 10200,
  paymentMethod: 'MOCK',
  recipient: '임준섭',
  phone: '010-1234-5678',
  zipCode: '06236',
  address1: '서울 강남구 테헤란로 1',
  address2: null,
  paidAt: '2026-09-19T14:22:00',
  cancelledAt: null,
  items: [{ id: 1, productId: 5, productName: '모두 머그컵 세트', optionLabel: '', imageUrl: null, unitPrice: 10200, quantity: 1, lineAmount: 10200, reviewId: null, reviewable: true }],
  ...over,
})

const staleTarget = { orderItemId: 1, productId: 5, productName: '모두 머그컵 세트', optionLabel: '', imageUrl: null, reviewable: true, reviewId: null }

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/reviews/new" element={<ReviewFormPage />} />
        <Route path="/reviews/:id/edit" element={<ReviewFormPage />} />
        <Route path="/products/:id/reviews" element={<ProductReviewsPage />} />
        <Route path="/orders/:id" element={<OrderDetailPage />} />
        <Route path="/my/reviews" element={<MyReviewsPage />} />
        <Route path="/my/coupons" element={<CouponsPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/coupons" element={<p>쿠폰존</p>} />
        <Route path="/" element={<NotificationButton />} />
      </Routes>
    </MemoryRouter>,
  )

describe('reads right after a write while the replica lags', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    clearRecentWrites()
  })
  afterEach(() => vi.useRealTimers())

  it('shows a just-written review in the lists and 내 리뷰 on the order detail even if the refetch is stale', async () => {
    const { calls } = fakeServer({
      'GET /api-public/v1/reviews/targets/1': () => staleTarget,
      'POST /api-public/v1/reviews': () => review(),
      'GET /api-public/v1/products/5/reviews': () => page([]),
      'GET /api-public/v1/products/5/reviews/summary': () => emptySummary,
      'GET /api-public/v1/orders/77': () => order(),
      'GET /api-public/v1/me/reviews': () => page([]),
    })
    const view = renderAt('/reviews/new?orderItemId=1')
    await userEvent.click(await screen.findByRole('radio', { name: '5점' }))
    await userEvent.type(screen.getByLabelText('솔직한 리뷰를 남겨 주세요'), '튼튼하고 색이 예뻐요 추천합니다')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    // 등록 뒤 상품 리뷰 목록: 서버(레플리카)는 빈 목록·0개를 주지만 방금 쓴 리뷰와 1개가 보인다
    expect(await screen.findByText('튼튼하고 색이 예뻐요 추천합니다')).toBeInTheDocument()
    expect(screen.getByText('리뷰 1개')).toBeInTheDocument()
    expect(screen.getByText('5.0')).toBeInTheDocument()
    view.unmount()

    // 주문 상세: 레플리카는 아직 '리뷰 쓰기'지만 '내 리뷰'로 보인다
    const detail = renderAt('/orders/77')
    const mine = await screen.findByRole('link', { name: '내 리뷰' })
    expect(mine).toHaveAttribute('href', '/reviews/70/edit')
    expect(screen.queryByRole('link', { name: '리뷰 쓰기' })).toBeNull()
    detail.unmount()

    // 그래도 쓰기 화면으로 오면 중복 등록 대신 수정 화면으로(리뷰 본문은 방금 쓴 응답으로)
    const again = renderAt('/reviews/new?orderItemId=1')
    expect(await screen.findByRole('button', { name: '수정 완료' })).toBeInTheDocument()
    expect(screen.getByLabelText('솔직한 리뷰를 남겨 주세요')).toHaveValue('튼튼하고 색이 예뻐요 추천합니다')
    expect(calls).not.toContain('GET /api-public/v1/reviews/70')
    again.unmount()

    // 내 리뷰 목록과 마이 탭 수
    renderAt('/my/reviews')
    expect(await screen.findByText('튼튼하고 색이 예뻐요 추천합니다')).toBeInTheDocument()
    expect((await getMyReviews(0, 1)).totalElements).toBe(1)
  })

  it('drops a just-deleted review and reopens 리뷰 쓰기 even if the refetch still has it', async () => {
    const existing = review({ id: 71 })
    fakeServer({
      'DELETE /api-public/v1/reviews/71': () => undefined,
      'GET /api-public/v1/me/reviews': () => page([existing, review({ id: 60, orderItemId: 9 })], 5),
      'GET /api-public/v1/orders/77': () => order({ items: [{ ...order().items[0], reviewId: 71, reviewable: false }] }),
      'GET /api-public/v1/reviews/targets/1': () => ({ ...staleTarget, reviewId: 71, reviewable: false }),
    })
    // 마이 탭이 먼저 5개를 봤다
    expect((await getMyReviews(0, 1)).totalElements).toBe(5)
    await deleteReview(71, existing)

    const mine = await getMyReviews()
    expect(mine.content.map((r) => r.id)).toEqual([60])
    expect(mine.totalElements).toBe(4)
    expect(await getReviewTarget(1)).toMatchObject({ reviewId: null, reviewable: true })
    renderAt('/orders/77')
    expect(await screen.findByRole('link', { name: '리뷰 쓰기' })).toHaveAttribute('href', '/reviews/new?orderItemId=1')
  })

  it('trusts the server again once the lag window has passed', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    fakeServer({ 'POST /api-public/v1/reviews': () => review(), 'GET /api-public/v1/me/reviews': () => page([]) })
    await writeReview(1, 5, '튼튼하고 색이 예뻐요 추천합니다')
    expect((await getMyReviews()).content).toHaveLength(1)
    vi.advanceTimersByTime(RECENT_MS)
    expect((await getMyReviews()).content).toHaveLength(0)
  })

  it('lists a redeemed coupon and counts it even if the refetch returns the old list', async () => {
    fakeServer({
      'GET /api-public/v1/me/coupons': () => [],
      'POST /api-public/v1/coupons/redeem': () => myCoupon({ id: 40, name: '코드 쿠폰', source: 'CODE' }),
    })
    renderAt('/my/coupons')
    expect(await screen.findByText('사용할 수 있는 쿠폰이 없습니다.')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('쿠폰 코드'), 'WELCOME10')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText('코드 쿠폰')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '사용 가능1' })).toBeInTheDocument()
  })

  it('drops a coupon just used for an order from the available list', async () => {
    fakeServer({
      'POST /api-public/v1/orders': () => order({ id: 88 }),
      'GET /api-public/v1/me/coupons': (_, url) => (url.searchParams.get('status') === 'AVAILABLE' ? [myCoupon({ id: 40, name: '쓴 쿠폰' })] : []),
    })
    await createOrder(1, [{ skuId: 3, quantity: 1 }], [], 0, 40)
    renderAt('/my/coupons')
    expect(await screen.findByText('사용할 수 있는 쿠폰이 없습니다.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: '사용 완료' }))
    expect(await screen.findByText('쓴 쿠폰')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '주문 보기' })).toHaveAttribute('href', '/orders/88')
  })

  it('decrements the badge at once and recounts only after the read request finished', async () => {
    let finishRead: () => void = () => {}
    let unread = 3
    const { calls } = fakeServer({ 'GET /api-public/v1/me/notifications/unread-count': () => ({ unread }) })
    // 읽음 요청은 테스트가 끝낼 때까지 붙잡아 둔다
    const fetchMock = vi.mocked(globalThis.fetch)
    const base = fetchMock.getMockImplementation()!
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).endsWith('/notifications/5/read')) {
        calls.push('POST read')
        await new Promise<void>((resolve) => {
          finishRead = () => {
            unread = 2
            resolve()
          }
        })
        return new Response(null, { status: 204 })
      }
      return base(input, init)
    })

    renderAt('/')
    const link = screen.getByRole('link', { name: '알림' })
    expect(await within(link).findByText('3')).toBeInTheDocument()

    let read!: Promise<void>
    act(() => {
      read = markNotificationRead(5)
    })
    expect(within(link).getByText('2')).toBeInTheDocument()
    const countsBefore = calls.filter((c) => c.endsWith('unread-count')).length
    await act(async () => {})
    expect(calls.filter((c) => c.endsWith('unread-count')).length).toBe(countsBefore) // 읽음이 끝나기 전에는 세지 않는다

    await act(async () => {
      finishRead()
      await read
    })
    expect(calls.filter((c) => c.endsWith('unread-count')).length).toBe(countsBefore + 1)
    expect(within(link).getByText('2')).toBeInTheDocument()
  })

  it('keeps a read notification read when the list comes back stale', async () => {
    const item: NotificationItem = { id: 5, campaignId: 1, title: '가을 특가', body: '보러 가기', imageUrl: null, path: '/', receivedAt: '2026-09-29T01:00:00', read: false }
    fakeServer({
      'GET /api-public/v1/me/notifications': () => page([item]),
      'POST /api-public/v1/me/notifications/5/read': () => undefined,
      'GET /api-public/v1/me/notifications/unread-count': () => ({ unread: 0 }),
    })
    const view = renderAt('/notifications')
    await userEvent.click(await screen.findByRole('button', { name: /가을 특가/ }))
    view.unmount()

    renderAt('/notifications')
    await screen.findByText('가을 특가')
    expect(screen.queryByRole('img', { name: '안 읽음' })).toBeNull()
  })

  it('shows a just-wished product in the wishlist and on its detail page', async () => {
    const card: ProductSummary = { id: 5, name: '모두 머그컵 세트', imageUrl: null, price: 10200, listPrice: null, discountRate: 0, soldOut: false, wished: false, reviewCount: 0, ratingAverage: 0 }
    const detail = { ...card, description: '', detail: null, images: [], wishCount: 3, categoryPath: [], optionGroups: [], skus: [] } as ProductDetail
    fakeServer({
      'POST /api-public/v1/wishlist/5': () => undefined,
      'DELETE /api-public/v1/wishlist/5': () => undefined,
      'GET /api-public/v1/wishlist': () => page([{ ...card, id: 2, wished: true }], 4),
      'GET /api-public/v1/products/5': () => detail,
    })
    expect((await getWishlist(0, 1)).totalElements).toBe(4)
    await setWish(5, true, card)
    const list = await getWishlist(0, 1)
    expect(list.content.map((p) => p.id)).toEqual([5, 2])
    expect(list.totalElements).toBe(5)
    expect(await getProduct(5)).toMatchObject({ wished: true, wishCount: 4 })

    await setWish(5, false)
    expect((await getWishlist()).content.map((p) => p.id)).toEqual([2])
    expect((await getWishlist(0, 1)).totalElements).toBe(4)
  })

  it('keeps today checked after attendance even if the promotion comes back stale', async () => {
    const promo = {
      id: 3,
      type: 'EVENT',
      eventKind: 'ATTENDANCE',
      title: '출석',
      products: [],
      coupons: [],
      attendance: { rewardPoints: 10, today: '2026-09-29', checkedToday: false, checkedDates: ['2026-09-28'], totalDays: 7 },
    } as unknown as PromotionDetail
    fakeServer({
      'GET /api-public/v1/promotions/3': () => promo,
      'POST /api-public/v1/promotions/3/attendance': () => ({ checkedDate: '2026-09-29', rewardPoints: 10, rewardMessage: null, checkedDates: ['2026-09-28', '2026-09-29'] }),
    })
    await checkAttendance(3)
    expect((await getPromotion(3)).attendance).toMatchObject({ checkedToday: true, checkedDates: ['2026-09-28', '2026-09-29'] })
  })
})
