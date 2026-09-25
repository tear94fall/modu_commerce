import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as coupons from '../api/coupons'
import { myCoupon } from '../test-fixtures/coupons'
import CouponsPage from './CouponsPage'

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/my/coupons']}>
      <Routes>
        <Route path="/my/coupons" element={<CouponsPage />} />
        <Route path="/coupons" element={<p>쿠폰존 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('CouponsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(coupons, 'getMyCouponCount').mockResolvedValue(1)
  })

  it('shows available coupons and switches tabs', async () => {
    const list = vi.spyOn(coupons, 'getMyCoupons').mockImplementation(async (status) =>
      status === 'AVAILABLE'
        ? [myCoupon({ discountType: 'PERCENT', discountValue: 10, maxDiscount: 5000, minOrderAmount: 30000, name: '10% 쿠폰' })]
        : status === 'USED'
          ? [myCoupon({ id: 32, name: '쓴 쿠폰', status: 'USED', usedAt: '2026-09-20T05:00:00', orderId: 77 })]
          : [],
    )
    renderPage()

    expect(await screen.findByText('10% 쿠폰')).toBeInTheDocument()
    expect(screen.getByText('10% 할인 (최대 5,000원)')).toBeInTheDocument()
    expect(screen.getByText('전체 상품 · 30,000원 이상 구매 시')).toBeInTheDocument()
    expect(screen.getByText('~ 2026.10.31')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '사용 가능1' })).toHaveAttribute('aria-selected', 'true')

    await userEvent.click(screen.getByRole('tab', { name: '사용 완료' }))
    expect(await screen.findByText('쓴 쿠폰')).toBeInTheDocument()
    expect(list).toHaveBeenLastCalledWith('USED')
    const card = screen.getByTestId('coupon-card')
    expect(within(card).getByText('사용 완료')).toHaveClass('coupon-badge')
    expect(within(card).getByRole('link', { name: '주문 보기' })).toHaveAttribute('href', '/orders/77')

    await userEvent.click(screen.getByRole('tab', { name: '만료' }))
    expect(await screen.findByText('만료된 쿠폰이 없습니다.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: /쿠폰 받으러 가기/ }))
    expect(screen.getByText('쿠폰존 화면')).toBeInTheDocument()
  })

  it('redeems a code and reloads the list', async () => {
    vi.spyOn(coupons, 'getMyCoupons').mockResolvedValueOnce([]).mockResolvedValue([myCoupon({ name: '코드 쿠폰', source: 'CODE' })])
    vi.spyOn(coupons, 'getMyCouponCount').mockResolvedValueOnce(0).mockResolvedValue(1)
    const redeem = vi.spyOn(coupons, 'redeemCoupon').mockResolvedValue(myCoupon())
    renderPage()

    expect(await screen.findByText('사용할 수 있는 쿠폰이 없습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled()
    await userEvent.type(screen.getByLabelText('쿠폰 코드'), 'welcome-10')
    expect(screen.getByLabelText('쿠폰 코드')).toHaveValue('WELCOME10')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    expect(redeem).toHaveBeenCalledWith('WELCOME10')
    expect(await screen.findByText('쿠폰을 받았습니다')).toBeInTheDocument()
    expect(await screen.findByText('코드 쿠폰')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '사용 가능1' })).toBeInTheDocument()
    expect(screen.getByLabelText('쿠폰 코드')).toHaveValue('')
  })

  it('shows the server message when the code is wrong', async () => {
    vi.spyOn(coupons, 'getMyCoupons').mockResolvedValue([])
    vi.spyOn(coupons, 'redeemCoupon').mockRejectedValue(new ApiError(404, JSON.stringify({ message: '쿠폰 코드를 확인해 주세요' })))
    renderPage()

    await userEvent.type(await screen.findByLabelText('쿠폰 코드'), 'NOPE')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(await screen.findByText('쿠폰 코드를 확인해 주세요')).toBeInTheDocument()
    expect(screen.getByLabelText('쿠폰 코드')).toHaveValue('NOPE')
  })
})
