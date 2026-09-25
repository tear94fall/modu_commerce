import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ProductSummary } from '../api/catalog'
import { ApiError } from '../api/client'
import * as coupons from '../api/coupons'
import * as promotions from '../api/promotions'
import { myCoupon, offer } from '../test-fixtures/coupons'
import PromotionPage from './PromotionPage'

const product = (over: Partial<ProductSummary> = {}): ProductSummary => ({
  id: 5,
  name: '모두 머그컵',
  imageUrl: null,
  price: 9000,
  listPrice: null,
  discountRate: 0,
  soldOut: false,
  wished: false,
  reviewCount: 0,
  ratingAverage: 0,
  ...over,
})

const exhibition = (over: Partial<promotions.PromotionDetail> = {}): promotions.PromotionDetail => ({
  id: 3,
  type: 'EXHIBITION',
  title: '홈카페 기획전',
  subtitle: '오늘의 커피 한 잔',
  description: '첫 줄\n둘째 줄',
  bannerImageUrl: null,
  bannerColor: '#7C3AED',
  startDate: '2026-09-01',
  endDate: '2026-09-30',
  status: 'ONGOING',
  products: [product(), product({ id: 6, name: '드립 주전자' })],
  eventKind: null,
  coupons: [],
  attendance: null,
  ...over,
})

const attendanceEvent = (over: Partial<promotions.AttendanceInfo> = {}, detail: Partial<promotions.PromotionDetail> = {}): promotions.PromotionDetail =>
  exhibition({
    id: 9,
    type: 'EVENT',
    title: '9월 출석 체크',
    subtitle: null,
    description: null,
    startDate: '2026-09-20',
    endDate: '2026-09-30',
    products: [],
    eventKind: 'ATTENDANCE',
    attendance: { rewardPoints: 10, today: '2026-09-25', checkedToday: false, checkedDates: ['2026-09-21', '2026-09-22'], totalDays: 11, ...over },
    ...detail,
  })

const renderPage = (id = 3) =>
  render(
    <MemoryRouter initialEntries={[`/promotions/${id}`]}>
      <Routes>
        <Route path="/promotions/:id" element={<PromotionPage />} />
        <Route path="/products/:id" element={<p>상품 상세</p>} />
      </Routes>
    </MemoryRouter>,
  )

const count = () => document.querySelector('.attend-count')?.textContent

describe('PromotionPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows an exhibition with its period, status and products', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(exhibition())
    renderPage()

    expect(await screen.findByRole('heading', { name: '홈카페 기획전' })).toBeInTheDocument()
    expect(screen.getByText('2026.09.01 ~ 2026.09.30')).toBeInTheDocument()
    expect(screen.getByText('진행 중')).toBeInTheDocument()
    expect(screen.getByText(/첫 줄/)).toHaveClass('promo-desc')
    expect(screen.getByText('드립 주전자')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: '모두 머그컵' }))
    expect(screen.getByText('상품 상세')).toBeInTheDocument()
  })

  it('says when an exhibition has no products yet', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(exhibition({ products: [], status: 'UPCOMING' }))
    renderPage()

    expect(await screen.findByText('준비 중인 상품이 없습니다')).toBeInTheDocument()
    expect(screen.getByText('예정')).toBeInTheDocument()
  })

  it('shows a not-found box on 404', async () => {
    vi.spyOn(promotions, 'getPromotion').mockRejectedValue(new ApiError(404, '{"message":"없음"}'))
    renderPage()

    expect(await screen.findByText('기획전을 찾을 수 없습니다')).toBeInTheDocument()
  })

  it('renders the attendance calendar and checks in', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(attendanceEvent())
    const check = vi.spyOn(promotions, 'checkAttendance').mockResolvedValue({
      checkedDate: '2026-09-25',
      rewardPoints: 10,
      rewardMessage: null,
      checkedDates: ['2026-09-21', '2026-09-22', '2026-09-25'],
    })
    renderPage(9)

    expect(await screen.findByText('매일 출석하면 10P')).toBeInTheDocument()
    expect(count()).toBe('출석 2일 / 11일')
    expect(document.querySelectorAll('.attend-grid .day:not(.blank)').length).toBe(11)
    expect(screen.getByTitle('9월 21일 출석')).toHaveClass('checked')
    expect(screen.getByTitle('9월 25일 (오늘)')).toHaveClass('today')
    expect(screen.getByTitle('9월 26일')).toHaveClass('future')

    await userEvent.click(screen.getByRole('button', { name: '출석 체크하기' }))
    expect(check).toHaveBeenCalledWith(9)
    expect(await screen.findByText('출석 완료! 10P 적립')).toBeInTheDocument()
    expect(count()).toBe('출석 3일 / 11일')
    expect(screen.getByTitle('9월 25일 출석 (오늘)')).toHaveClass('checked')
    expect(screen.getByRole('button', { name: '오늘 출석 완료' })).toBeDisabled()
  })

  it('shows the reason when no points were credited', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(attendanceEvent({ rewardPoints: null }))
    vi.spyOn(promotions, 'checkAttendance').mockResolvedValue({
      checkedDate: '2026-09-25',
      rewardPoints: 0,
      rewardMessage: '오늘 적립 한도를 넘었습니다',
      checkedDates: ['2026-09-21', '2026-09-22', '2026-09-25'],
    })
    renderPage(9)

    expect(await screen.findByText('출석 체크')).toHaveClass('attend-reward')
    await userEvent.click(screen.getByRole('button', { name: '출석 체크하기' }))
    expect(await screen.findByText('출석 완료 · 오늘 적립 한도를 넘었습니다')).toBeInTheDocument()
  })

  it('marks today as checked on 409', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(attendanceEvent())
    vi.spyOn(promotions, 'checkAttendance').mockRejectedValue(new ApiError(409, '{"message":"오늘은 이미 출석했습니다"}'))
    renderPage(9)

    await userEvent.click(await screen.findByRole('button', { name: '출석 체크하기' }))
    expect(await screen.findByText('오늘은 이미 출석했습니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '오늘 출석 완료' })).toBeDisabled()
    expect(count()).toBe('출석 3일 / 11일')
  })

  it('asks to retry on 503 without marking today', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(attendanceEvent())
    vi.spyOn(promotions, 'checkAttendance').mockRejectedValue(new ApiError(503, '{"message":"point down"}'))
    renderPage(9)

    await userEvent.click(await screen.findByRole('button', { name: '출석 체크하기' }))
    expect(await screen.findByText('잠시 후 다시 시도해 주세요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '출석 체크하기' })).toBeEnabled()
    expect(count()).toBe('출석 2일 / 11일')
  })

  it('disables the button outside the event period', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(attendanceEvent({}, { status: 'ENDED' }))
    renderPage(9)

    expect(await screen.findByRole('button', { name: '진행 기간이 아닙니다' })).toBeDisabled()
  })

  it('pages a long event month by month', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(attendanceEvent({ totalDays: 92 }, { startDate: '2026-08-01', endDate: '2026-10-31' }))
    renderPage(9)

    expect(await screen.findByText('2026년 9월')).toBeInTheDocument()
    expect(document.querySelectorAll('.attend-grid .day:not(.blank)').length).toBe(30)
    await userEvent.click(screen.getByRole('button', { name: '다음 달' }))
    expect(screen.getByText('2026년 10월')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음 달' })).toBeDisabled()
  })

  it('shows exhibition coupons with 받기 buttons', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(exhibition({ coupons: [offer({ couponId: 4, name: '홈카페 10%', discountType: 'PERCENT', discountValue: 10, maxDiscount: 3000 })] }))
    const download = vi.spyOn(coupons, 'downloadCoupon').mockResolvedValue(myCoupon({ couponId: 4 }))
    renderPage()

    expect(await screen.findByRole('heading', { name: '기획전 쿠폰' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '홈카페 10% 받기' }))
    expect(download).toHaveBeenCalledWith(4)
    expect(await screen.findByRole('button', { name: '홈카페 10% 받음' })).toBeDisabled()
    expect(screen.getByText('드립 주전자')).toBeInTheDocument()
  })

  it('has no coupon section for an exhibition without coupons', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(exhibition())
    renderPage()
    expect(await screen.findByText('드립 주전자')).toBeInTheDocument()
    expect(screen.queryByText('기획전 쿠폰')).not.toBeInTheDocument()
  })

  const couponEvent = (over: Partial<promotions.PromotionDetail> = {}) =>
    exhibition({
      id: 12,
      type: 'EVENT',
      eventKind: 'COUPON',
      title: '가을 쿠폰 팩',
      products: [],
      attendance: null,
      coupons: [offer({ couponId: 1, name: '쿠폰 A' }), offer({ couponId: 2, name: '쿠폰 B' }), offer({ couponId: 3, name: '쿠폰 C', downloaded: true })],
      ...over,
    })

  it('claims every event coupon at once', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(couponEvent())
    const claim = vi.spyOn(coupons, 'claimEventCoupons').mockResolvedValue({ issued: [myCoupon({ couponId: 1 }), myCoupon({ id: 32, couponId: 2 })], alreadyHad: 1 })
    renderPage(12)

    expect(await screen.findByText('쿠폰 A')).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: '출석 달력' })).not.toBeInTheDocument()
    expect(screen.getAllByText('받음')).toHaveLength(1)

    await userEvent.click(screen.getByRole('button', { name: '쿠폰 한 번에 받기' }))
    expect(claim).toHaveBeenCalledWith(12)
    expect(await screen.findByText('쿠폰 2장을 받았습니다')).toBeInTheDocument()
    expect(screen.getAllByText('받음')).toHaveLength(3)
    expect(screen.getByRole('button', { name: '모두 받았어요' })).toBeDisabled()
  })

  it('marks everything received on 409', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(couponEvent())
    vi.spyOn(coupons, 'claimEventCoupons').mockRejectedValue(new ApiError(409, JSON.stringify({ message: '이미 받은 쿠폰입니다' })))
    renderPage(12)

    await userEvent.click(await screen.findByRole('button', { name: '쿠폰 한 번에 받기' }))
    expect(await screen.findByText('이미 받은 쿠폰입니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '모두 받았어요' })).toBeDisabled()
  })

  it('disables the claim button when all are received or the event is over', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValueOnce(couponEvent({ status: 'ENDED' }))
    const { unmount } = renderPage(12)
    expect(await screen.findByRole('button', { name: '진행 기간이 아닙니다' })).toBeDisabled()
    unmount()

    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(couponEvent({ coupons: [offer({ downloaded: true })] }))
    renderPage(12)
    expect(await screen.findByRole('button', { name: '모두 받았어요' })).toBeDisabled()
  })

  it('treats an old event without eventKind as attendance', async () => {
    vi.spyOn(promotions, 'getPromotion').mockResolvedValue(attendanceEvent({}, { eventKind: null }))
    renderPage(9)
    expect(await screen.findByRole('button', { name: '출석 체크하기' })).toBeEnabled()
  })
})
