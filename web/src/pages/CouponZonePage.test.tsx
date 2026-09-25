import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as coupons from '../api/coupons'
import { myCoupon, offer } from '../test-fixtures/coupons'
import CouponZonePage from './CouponZonePage'

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/coupons']}>
      <Routes>
        <Route path="/coupons" element={<CouponZonePage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('CouponZonePage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('downloads a coupon and marks it 받음', async () => {
    vi.spyOn(coupons, 'getDownloadableCoupons').mockResolvedValue([
      offer(),
      offer({ couponId: 2, name: '받은 쿠폰', downloaded: true }),
      offer({ couponId: 3, name: '인기 쿠폰', soldOut: true }),
    ])
    const download = vi.spyOn(coupons, 'downloadCoupon').mockResolvedValue(myCoupon())
    renderPage()

    expect(await screen.findByRole('button', { name: '가을 맞이 3천원 받기' })).toBeEnabled()
    expect(screen.getAllByText('2026.10.31까지')).toHaveLength(3)
    expect(screen.getAllByText('3,000원 할인')).toHaveLength(3)
    expect(screen.getByRole('button', { name: '받은 쿠폰 받음' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '인기 쿠폰 소진' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: '가을 맞이 3천원 받기' }))
    expect(download).toHaveBeenCalledWith(1)
    expect(await screen.findByText('쿠폰을 받았습니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가을 맞이 3천원 받음' })).toBeDisabled()
  })

  it('marks the coupon as downloaded on 409 and shows the message', async () => {
    vi.spyOn(coupons, 'getDownloadableCoupons').mockResolvedValue([offer()])
    vi.spyOn(coupons, 'downloadCoupon').mockRejectedValue(new ApiError(409, JSON.stringify({ message: '이미 받은 쿠폰입니다' })))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '가을 맞이 3천원 받기' }))
    expect(await screen.findByText('이미 받은 쿠폰입니다')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가을 맞이 3천원 받음' })).toBeDisabled()
  })

  it('says when there is nothing to download', async () => {
    vi.spyOn(coupons, 'getDownloadableCoupons').mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('지금 받을 수 있는 쿠폰이 없습니다.')).toBeInTheDocument()
  })
})
