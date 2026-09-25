import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as cart from '../api/cart'
import * as catalog from '../api/catalog'
import { ApiError } from '../api/client'
import * as coupons from '../api/coupons'
import * as orders from '../api/orders'
import * as points from '../api/points'
import { applicable } from '../test-fixtures/coupons'
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
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(0)
    vi.spyOn(coupons, 'getApplicableCoupons').mockResolvedValue([])
  })

  it('orders the cart lines to the default address and clears them from the cart', async () => {
    vi.spyOn(cart, 'getCart').mockResolvedValue({ items: [cartItem, { ...cartItem, id: 10, productName: '다른 상품' }], totalAmount: 0, itemCount: 2 })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([address({ id: 2, isDefault: false, recipient: '김모두' }), address()])
    const create = vi.spyOn(orders, 'createOrder').mockResolvedValue(orderDetail)
    renderAt('/checkout?cartItemIds=9')

    expect(await screen.findByText('임준섭')).toBeInTheDocument()
    expect(screen.getByText('기본')).toBeInTheDocument()
    expect(screen.getByText('모두 스티커 팩')).toBeInTheDocument()
    expect(screen.queryByText('다른 상품')).not.toBeInTheDocument()
    expect(screen.getAllByText('10,200원').length).toBeGreaterThanOrEqual(2)

    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))
    expect(create).toHaveBeenCalledWith(1, [{ skuId: 50, quantity: 2 }], [9], 0, null)
    expect(await screen.findByText('주문 상세 화면')).toBeInTheDocument()
  })

  it('builds a single line for buy-now and requires an address', async () => {
    vi.spyOn(catalog, 'getProduct').mockResolvedValue({
      id: 5, name: '모두 스티커 팩', description: '', detail: null, images: [], price: 5000, listPrice: null, discountRate: 0, soldOut: false, wished: false, wishCount: 0, reviewCount: 0, ratingAverage: 0, categoryPath: [],
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

  it('uses points up to the smaller of balance and total, and sends them with the order', async () => {
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(3000)
    vi.spyOn(cart, 'getCart').mockResolvedValue({ items: [cartItem], totalAmount: 0, itemCount: 1 })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([address()])
    const create = vi.spyOn(orders, 'createOrder').mockResolvedValue(orderDetail)
    renderAt('/checkout?cartItemIds=9')

    expect(await screen.findByText('보유 3,000P')).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText('사용 포인트'), '99999')
    expect(screen.getByText('-3,000원')).toBeInTheDocument()
    expect(screen.getAllByText('7,200원').length).toBeGreaterThan(0)

    await userEvent.clear(screen.getByLabelText('사용 포인트'))
    await userEvent.type(screen.getByLabelText('사용 포인트'), '1200')
    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))
    expect(create).toHaveBeenCalledWith(1, [{ skuId: 50, quantity: 2 }], [9], 1200, null)
  })

  it('전액 사용 caps at the order total when the balance is bigger', async () => {
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(50000)
    vi.spyOn(cart, 'getCart').mockResolvedValue({ items: [cartItem], totalAmount: 0, itemCount: 1 })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([address()])
    const create = vi.spyOn(orders, 'createOrder').mockResolvedValue(orderDetail)
    renderAt('/checkout?cartItemIds=9')

    await userEvent.click(await screen.findByRole('button', { name: '전액 사용' }))
    expect(screen.getByLabelText('사용 포인트')).toHaveValue(10200)
    expect(screen.getAllByText('0원').length).toBeGreaterThan(0)
    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))
    expect(create).toHaveBeenCalledWith(1, [{ skuId: 50, quantity: 2 }], [9], 10200, null)
  })

  it('applies a coupon before points, re-clamps the points and sends userCouponId', async () => {
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(10000)
    vi.spyOn(cart, 'getCart').mockResolvedValue({ items: [cartItem], totalAmount: 0, itemCount: 1 })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([address()])
    const list = vi.spyOn(coupons, 'getApplicableCoupons').mockResolvedValue([
      applicable(),
      applicable({ id: 32, name: '5만원 이상 쿠폰', discount: 0, applicable: false, reason: '50,000원 이상 주문 시 사용 가능', minOrderAmount: 50000 }),
    ])
    const create = vi.spyOn(orders, 'createOrder').mockResolvedValue(orderDetail)
    renderAt('/checkout?cartItemIds=9')

    expect(await screen.findByText('사용 가능 1장')).toBeInTheDocument()
    expect(list).toHaveBeenCalledWith([{ skuId: 50, quantity: 2 }])
    expect(screen.getByText('쿠폰을 선택해 주세요')).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText('사용 포인트'), '9000')
    expect(screen.getAllByText('1,200원').length).toBeGreaterThan(0)

    await userEvent.click(screen.getByRole('button', { name: '쿠폰 선택' }))
    const panel = screen.getByRole('dialog')
    expect(within(panel).getByRole('button', { name: '5만원 이상 쿠폰' })).toBeDisabled()
    expect(within(panel).getByText('50,000원 이상 주문 시 사용 가능')).toBeInTheDocument()
    await userEvent.click(within(panel).getByRole('button', { name: '가을 맞이 3천원' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('가을 맞이 3천원')).toBeInTheDocument()
    expect(screen.getByText('쿠폰 할인')).toBeInTheDocument()
    expect(screen.getAllByText('-3,000원').length).toBe(2) // 쿠폰 칸 + 결제 금액
    // 10,200 − 3,000 = 7,200 까지만 포인트를 쓸 수 있다.
    expect(screen.getByLabelText('사용 포인트')).toHaveValue(7200)
    expect(screen.getByText('최대 7,200P까지 쓸 수 있어요. 1P = 1원')).toBeInTheDocument()
    expect(screen.getAllByText('0원').length).toBeGreaterThan(0)

    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))
    expect(create).toHaveBeenCalledWith(1, [{ skuId: 50, quantity: 2 }], [9], 7200, 31)
  })

  it('drops the coupon when choosing 선택 안 함', async () => {
    vi.spyOn(cart, 'getCart').mockResolvedValue({ items: [cartItem], totalAmount: 0, itemCount: 1 })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([address()])
    vi.spyOn(coupons, 'getApplicableCoupons').mockResolvedValue([applicable()])
    const create = vi.spyOn(orders, 'createOrder').mockResolvedValue(orderDetail)
    renderAt('/checkout?cartItemIds=9')

    await userEvent.click(await screen.findByRole('button', { name: '쿠폰 선택' }))
    await userEvent.click(screen.getByRole('button', { name: '가을 맞이 3천원' }))
    expect(screen.getAllByText('7,200원').length).toBeGreaterThan(0)
    await userEvent.click(screen.getByRole('button', { name: '쿠폰 선택' }))
    await userEvent.click(screen.getByRole('button', { name: '선택 안 함' }))
    expect(screen.queryByText('쿠폰 할인')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))
    expect(create).toHaveBeenCalledWith(1, [{ skuId: 50, quantity: 2 }], [9], 0, null)
  })

  it('clears the coupon when the order is refused because of it', async () => {
    vi.spyOn(cart, 'getCart').mockResolvedValue({ items: [cartItem], totalAmount: 0, itemCount: 1 })
    vi.spyOn(orders, 'getAddresses').mockResolvedValue([address()])
    const list = vi.spyOn(coupons, 'getApplicableCoupons').mockResolvedValueOnce([applicable()]).mockResolvedValue([])
    vi.spyOn(orders, 'createOrder').mockRejectedValue(new ApiError(400, JSON.stringify({ message: '사용할 수 없는 쿠폰입니다' })))
    renderAt('/checkout?cartItemIds=9')

    await userEvent.click(await screen.findByRole('button', { name: '쿠폰 선택' }))
    await userEvent.click(screen.getByRole('button', { name: '가을 맞이 3천원' }))
    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))

    expect(await screen.findByText('사용할 수 없는 쿠폰입니다')).toBeInTheDocument()
    expect(await screen.findByText('사용할 수 있는 쿠폰이 없습니다')).toBeInTheDocument()
    expect(screen.queryByText('쿠폰 할인')).not.toBeInTheDocument()
    expect(list).toHaveBeenCalledTimes(2)
  })
})
