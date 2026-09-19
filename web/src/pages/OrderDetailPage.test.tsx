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
  paymentMethod: 'MOCK',
  recipient: '임준섭',
  phone: '010-1234-5678',
  zipCode: '06236',
  address1: '서울 강남구 테헤란로 1',
  address2: '101동 101호',
  paidAt: '2026-09-19T14:22:00',
  cancelledAt: null,
  items: [{ id: 1, productId: 5, productName: '모두 스티커 팩', optionLabel: '블랙 / 10', imageUrl: null, unitPrice: 5100, quantity: 2, lineAmount: 10200 }],
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
})
