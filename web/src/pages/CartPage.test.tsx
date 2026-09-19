import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as cart from '../api/cart'
import CartPage from './CartPage'

const item = (over: Partial<cart.CartItem> = {}): cart.CartItem => ({
  id: 1,
  productId: 5,
  skuId: 50,
  productName: '모두 스티커 팩',
  optionLabel: '블랙 / 10',
  imageUrl: null,
  unitPrice: 5100,
  quantity: 1,
  stock: 7,
  available: true,
  lineAmount: 5100,
  ...over,
})
const cartOf = (items: cart.CartItem[]): cart.Cart => ({ items, totalAmount: items.reduce((s, i) => s + i.lineAmount, 0), itemCount: items.length })

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/cart']}>
      <Routes>
        <Route path="/cart" element={<CartPage />} />
        <Route path="/checkout" element={<p>주문서 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('CartPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('changes quantity, keeps unorderable rows out of the total, and goes to checkout with orderable ids', async () => {
    vi.spyOn(cart, 'getCart').mockResolvedValue(cartOf([item(), item({ id: 2, productName: '품절 머그', optionLabel: '', stock: 0, unitPrice: 18000, lineAmount: 18000 })]))
    const change = vi.spyOn(cart, 'changeCartQuantity').mockResolvedValue(item({ quantity: 2 }))
    renderPage()

    expect(await screen.findByText('모두 스티커 팩')).toBeInTheDocument()
    expect(screen.getByText('재고 부족 (남은 수량 0)')).toBeInTheDocument()
    expect(screen.getByText('총 1개')).toBeInTheDocument()

    await userEvent.click(screen.getAllByRole('button', { name: '수량 늘리기' })[0])
    expect(change).toHaveBeenCalledWith(1, 2)

    await userEvent.click(screen.getByRole('button', { name: '주문하기' }))
    expect(screen.getByText('주문서 화면')).toBeInTheDocument()
  })

  it('removes a row and shows the empty state', async () => {
    vi.spyOn(cart, 'getCart').mockResolvedValueOnce(cartOf([item()])).mockResolvedValue(cartOf([]))
    const remove = vi.spyOn(cart, 'removeCartItem').mockResolvedValue(undefined)
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '삭제' }))
    expect(remove).toHaveBeenCalledWith(1)
    expect(await screen.findByText('장바구니가 비었습니다.')).toBeInTheDocument()
  })
})
