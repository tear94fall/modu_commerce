import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as cart from '../api/cart'
import * as catalog from '../api/catalog'
import * as orders from '../api/orders'
import CheckoutPage from './CheckoutPage'

const address = (over: Partial<orders.Address> = {}): orders.Address => ({
  id: 1,
  recipient: '임준섭',
  phone: '010-1234-5678',
  zipCode: '06236',
  address1: '서울 강남구 테헤란로 1',
  address2: '101동 101호',
  isDefault: true,
  ...over,
})
const cartItem: cart.CartItem = { id: 9, productId: 5, skuId: 50, productName: '모두 스티커 팩', optionLabel: '블랙 / 10', imageUrl: null, unitPrice: 5100, quantity: 2, stock: 7, available: true, lineAmount: 10200 }
const orderDetail = { id: 77 } as orders.OrderDetail

const renderAt = (url: string) =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/checkout" element={<CheckoutPage />} />
        <Route path="/orders/:id" element={<p>주문 상세 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('CheckoutPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('orders the cart lines to the default address and clears them from the cart', async () => {
    vi.spyOn(cart, 'getCart').mockResolvedValue({ items: [cartItem, { ...cartItem, id: 10, productName: '다른 상품' }], totalAmount: 0, itemCount: 2 })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([address({ id: 2, isDefault: false, recipient: '김모두' }), address()])
    const create = vi.spyOn(orders, 'createOrder').mockResolvedValue(orderDetail)
    renderAt('/checkout?cartItemIds=9')

    expect(await screen.findByText('임준섭')).toBeInTheDocument()
    expect(screen.getByText('기본')).toBeInTheDocument()
    expect(screen.getByText('모두 스티커 팩')).toBeInTheDocument()
    expect(screen.queryByText('다른 상품')).not.toBeInTheDocument()
    expect(screen.getAllByText('10,200원').length).toBe(2)

    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))
    expect(create).toHaveBeenCalledWith(1, [{ skuId: 50, quantity: 2 }], [9])
    expect(await screen.findByText('주문 상세 화면')).toBeInTheDocument()
  })

  it('builds a single line for buy-now and requires an address', async () => {
    vi.spyOn(catalog, 'getProduct').mockResolvedValue({
      id: 5, name: '모두 스티커 팩', description: '', detail: null, images: [], price: 5000, listPrice: null, discountRate: 0, soldOut: false, wished: false, wishCount: 0, categoryPath: [],
      optionGroups: [], skus: [{ id: 51, optionValueIds: [], optionLabel: '그린 / 20', extraPrice: 100, stock: 3 }],
    })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([])
    const create = vi.spyOn(orders, 'createAddress').mockResolvedValue(address({ id: 3 }))
    vi.spyOn(orders, 'getAddresses').mockResolvedValueOnce([]).mockResolvedValue([address({ id: 3 })])
    renderAt('/checkout?productId=5&skuId=51&quantity=3')

    expect(await screen.findByText('배송지를 등록해 주세요')).toBeInTheDocument()
    expect(screen.getByText('5,100원 × 3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '결제하기' })).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: '배송지 추가' }))
    await userEvent.type(screen.getByLabelText('받는 사람'), '임준섭')
    await userEvent.type(screen.getByLabelText('연락처'), '010-1234-5678')
    await userEvent.type(screen.getByLabelText('우편번호'), '0623')
    await userEvent.type(screen.getByLabelText('주소'), '서울 강남구 테헤란로 1')
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled() // 우편번호 5자리
    await userEvent.type(screen.getByLabelText('우편번호'), '6')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    expect(create).toHaveBeenCalledWith({ recipient: '임준섭', phone: '010-1234-5678', zipCode: '06236', address1: '서울 강남구 테헤란로 1', address2: null, isDefault: false })
    expect(await screen.findByText('임준섭')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '결제하기' })).toBeEnabled()
  })
})
