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
    const cancel = vi.spyOn(orders, 'cancelOrder').mockResolvedValue(order({ status: 'CANCELLED', cancelledBy: 'CUSTOMER', cancelReason: '단순 변심', cancelledAt: '2026-09-19T14:30:00' }))
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
    expect(cancel).toHaveBeenCalledWith(77, '단순 변심')
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

  it('says the point refund is still being processed while the refund is pending', async () => {
    vi.spyOn(orders, 'getOrder').mockResolvedValue(
      order({ status: 'CANCELLED', cancelledAt: '2026-09-19T14:30:00', pointAmount: 3000, paymentAmount: 7200, pointRefundStatus: 'PENDING' }),
    )
    render(
      <MemoryRouter initialEntries={['/orders/77']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('포인트 사용 · 포인트 환불 처리 중')).toBeInTheDocument()
  })

  it('says the points were refunded once done (or for an older server without the field)', async () => {
    vi.spyOn(orders, 'getOrder')
      .mockResolvedValueOnce(order({ status: 'CANCELLED', cancelledAt: '2026-09-19T14:30:00', pointAmount: 3000, pointRefundStatus: 'DONE' }))
    render(
      <MemoryRouter initialEntries={['/orders/77']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('포인트 사용 (환불됨)')).toBeInTheDocument()
    expect(screen.queryByText(/환불 처리 중/)).not.toBeInTheDocument()
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

    it('asks the customer to check with us when the earn failed', async () => {
      vi.spyOn(orders, 'getOrder').mockResolvedValue(order({ status: 'DELIVERED', earn: { status: 'FAILED', points: 1200, rate: 3 }, expectedEarn: null }))
      renderOrder()
      expect(await screen.findByText('적립 확인 중 · 고객센터에 문의해 주세요')).toBeInTheDocument()
      expect(screen.queryByText('적립 처리 중')).not.toBeInTheDocument()
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

  it('shows the carrier, tracking number and the progress timeline once shipped', async () => {
    vi.spyOn(orders, 'getOrder').mockResolvedValue(
      order({
        status: 'SHIPPING',
        carrier: 'CJ대한통운',
        trackingNo: '1234-5678',
        shippedAt: '2026-09-20T01:00:00',
        events: [
          { id: 1, fromStatus: null, toStatus: 'PAID', actorType: 'CUSTOMER', reason: null, carrier: null, trackingNo: null, at: '2026-09-19T14:22:00' },
          { id: 2, fromStatus: 'PAID', toStatus: 'SHIPPING', actorType: 'STAFF', reason: null, carrier: 'CJ대한통운', trackingNo: '1234-5678', at: '2026-09-20T01:00:00' },
        ],
      }),
    )
    render(
      <MemoryRouter initialEntries={['/orders/77']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )
    const shipping = await screen.findByRole('region', { name: '배송' })
    expect(shipping).toHaveTextContent('CJ대한통운')
    expect(shipping).toHaveTextContent('1234-5678')
    const steps = screen.getByRole('region', { name: '진행' })
    expect(steps).toHaveTextContent('주문 완료')
    expect(steps).toHaveTextContent('배송 시작')
    expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument()
  })

  it('shows who cancelled and why', async () => {
    vi.spyOn(orders, 'getOrder').mockResolvedValue(order({ status: 'CANCELLED', cancelledAt: '2026-09-20T02:00:00', cancelledBy: 'STAFF', cancelReason: '재고 부족' }))
    render(
      <MemoryRouter initialEntries={['/orders/77']}>
        <Routes>
          <Route path="/orders/:id" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )
    expect(await screen.findByText(/판매자 취소 · 재고 부족/)).toBeInTheDocument()
  })
})
