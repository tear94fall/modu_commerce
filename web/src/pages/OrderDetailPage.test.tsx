import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as orders from '../api/orders'
import OrderDetailPage from './OrderDetailPage'

const order = (over: Partial<orders.OrderDetail> = {}): orders.OrderDetail => ({
  id: 77,
  orderNo: '20260919-ABC123',
  status: 'PAID',
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
  address2: '101동 101호',
  paidAt: '2026-09-19T14:22:00',
  cancelledAt: null,
  items: [{ id: 1, productId: 5, productName: '모두 스티커 팩', optionLabel: '블랙 / 10', imageUrl: null, unitPrice: 5100, quantity: 2, lineAmount: 10200, reviewId: null, reviewable: true }],
  ...over,
})

describe('OrderDetailPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows the order and cancels it after confirmation', async () => {
    vi.spyOn(orders, 'getOrder').mockResolvedValue(order())
    const cancel = vi.spyOn(orders, 'cancelOrder').mockResolvedValue(order({ status: 'CANCELLED', cancelledAt: '2026-09-19T14:30:00' }))
    render(
      <MemoryRouter initialEntries={['/orders/77']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('주문번호 20260919-ABC123')).toBeInTheDocument()
    expect(screen.getByText('결제완료')).toBeInTheDocument()
    expect(screen.getByText('모의 결제 (실제 결제 없음)')).toBeInTheDocument()
    expect(screen.getByText('(06236) 서울 강남구 테헤란로 1 101동 101호')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '주문 취소' }))
    await userEvent.click(screen.getByRole('dialog').querySelector('.actions button:last-child')!)
    expect(cancel).toHaveBeenCalledWith(77)
    expect(await screen.findByText(/^취소 2026\.09\.19 /)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument()
  })

  it('shows the coupon discount in the breakdown', async () => {
    vi.spyOn(orders, 'getOrder').mockResolvedValue(order({ couponDiscount: 3000, couponName: '가을 맞이 3천원', pointAmount: 200, paymentAmount: 7000 }))
    render(
      <MemoryRouter initialEntries={['/orders/77']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('쿠폰 할인 (가을 맞이 3천원)')).toBeInTheDocument()
    expect(screen.getByText('-3,000원')).toBeInTheDocument()
    expect(screen.getByText('-200원')).toBeInTheDocument()
    expect(screen.getByText('7,000원')).toBeInTheDocument()
  })

  describe('purchase earn line', () => {
    const renderOrder = () =>
      render(
        <MemoryRouter initialEntries={['/orders/77']}>
          <Routes>
            <Route path="/orders/:id" element={<OrderDetailPage />} />
          </Routes>
        </MemoryRouter>,
      )

    it('shows the expected earn while paid or shipping', async () => {
      vi.spyOn(orders, 'getOrder').mockResolvedValue(order({ status: 'SHIPPING', paymentAmount: 40000, earn: null, expectedEarn: { points: 1200, rate: 3 } }))
      renderOrder()
      expect(await screen.findByText('배송 완료 시 3% 적립 예정 · 1,200P')).toBeInTheDocument()
    })

    it('shows the earned points once delivered', async () => {
      vi.spyOn(orders, 'getOrder').mockResolvedValue(order({ status: 'DELIVERED', earn: { status: 'DONE', points: 1200, rate: 3 }, expectedEarn: null }))
      renderOrder()
      expect(await screen.findByText('1,200P 적립 완료')).toBeInTheDocument()
    })

    it('says the earn is being processed while pending', async () => {
      vi.spyOn(orders, 'getOrder').mockResolvedValue(order({ status: 'DELIVERED', earn: { status: 'PENDING', points: 1200, rate: 3 }, expectedEarn: null }))
      renderOrder()
      expect(await screen.findByText('적립 처리 중')).toBeInTheDocument()
    })

    it('shows nothing for no earn or an older server without the fields', async () => {
      vi.spyOn(orders, 'getOrder').mockResolvedValueOnce(order({ status: 'DELIVERED', earn: { status: 'NONE', points: 0, rate: 0 }, expectedEarn: null }))
      const { unmount } = renderOrder()
      expect(await screen.findByText('주문번호 20260919-ABC123')).toBeInTheDocument()
      expect(screen.queryByText(/적립/)).not.toBeInTheDocument()
      unmount()

      vi.spyOn(orders, 'getOrder').mockResolvedValue(order({ status: 'CANCELLED' }))
      renderOrder()
      expect(await screen.findByText('주문번호 20260919-ABC123')).toBeInTheDocument()
      expect(screen.queryByText(/적립/)).not.toBeInTheDocument()
    })
  })
})
