import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as catalog from '../api/catalog'
import * as coupons from '../api/coupons'
import * as me from '../api/me'
import * as orders from '../api/orders'
import * as points from '../api/points'
import * as reviews from '../api/reviews'
import { page, product } from '../test-fixtures/catalog'
import MyPage from './MyPage'

const renderMy = () =>
  render(
    <MemoryRouter initialEntries={['/my']}>
      <Routes>
        <Route path="/my" element={<MyPage />} />
        <Route path="/orders" element={<p>주문 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

const order = (id: number, status: orders.OrderStatus) => ({ id, status }) as orders.OrderSummary
const benefits = () => screen.getByRole('navigation', { name: '내 혜택' })

describe('MyPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(me, 'getProfile').mockResolvedValue({ name: '임준섭', email: 'me@modu.local', picture: '' })
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(600)
    vi.spyOn(coupons, 'getMyCouponCount').mockResolvedValue(3)
    vi.spyOn(catalog, 'getWishlist').mockResolvedValue(page([product()], 12))
    vi.spyOn(reviews, 'getMyReviews').mockResolvedValue(page([], 4))
    vi.spyOn(orders, 'getOrders').mockResolvedValue(
      page([order(1, 'PAID'), order(2, 'SHIPPING'), order(3, 'SHIPPING'), order(4, 'DELIVERED'), order(5, 'DELIVERED'), order(6, 'DELIVERED'), order(7, 'CANCELLED')]),
    )
  })
  afterEach(() => {
    delete window.ModuApp
  })

  it('shows the profile and the four benefit tiles with their counts and links', async () => {
    renderMy()

    expect(await screen.findByText('임준섭')).toBeInTheDocument()
    expect(screen.getByText('me@modu.local')).toBeInTheDocument()
    const tiles = within(benefits())
    expect(await tiles.findByRole('link', { name: '포인트 600P' })).toHaveAttribute('href', '/points')
    expect(await tiles.findByRole('link', { name: '쿠폰 3장' })).toHaveAttribute('href', '/my/coupons')
    expect(await tiles.findByRole('link', { name: '찜 12개' })).toHaveAttribute('href', '/wishlist')
    expect(await tiles.findByRole('link', { name: '리뷰 4개' })).toHaveAttribute('href', '/my/reviews')
    expect(catalog.getWishlist).toHaveBeenCalledWith(0, 1)
    expect(reviews.getMyReviews).toHaveBeenCalledWith(0, 1)
  })

  it('shows "-" for counts that failed to load but keeps the page', async () => {
    vi.spyOn(points, 'getMyPoints').mockRejectedValue(new Error('503'))
    vi.spyOn(coupons, 'getMyCouponCount').mockRejectedValue(new Error('500'))
    vi.spyOn(catalog, 'getWishlist').mockRejectedValue(new Error('500'))
    vi.spyOn(reviews, 'getMyReviews').mockRejectedValue(new Error('500'))
    vi.spyOn(orders, 'getOrders').mockRejectedValue(new Error('500'))
    renderMy()

    const tiles = within(benefits())
    expect(await tiles.findByRole('link', { name: '포인트 -' })).toBeInTheDocument()
    expect(await tiles.findByRole('link', { name: '쿠폰 -' })).toBeInTheDocument()
    expect(await tiles.findByRole('link', { name: '찜 -' })).toBeInTheDocument()
    expect(await tiles.findByRole('link', { name: '리뷰 -' })).toBeInTheDocument()
    const orderCard = screen.getByRole('region', { name: '주문·배송' })
    expect(await within(orderCard).findByRole('link', { name: '취소 -' })).toBeInTheDocument()
    expect(screen.getByText('임준섭')).toBeInTheDocument()
  })

  it('counts my orders by status and opens the orders page', async () => {
    renderMy()

    const card = screen.getByRole('region', { name: '주문·배송' })
    const steps = await within(card).findByRole('link', { name: '결제완료 1, 배송중 2, 배송완료 3' })
    expect(within(card).getByRole('link', { name: '취소 1' })).toHaveAttribute('href', '/orders')
    expect(orders.getOrders).toHaveBeenCalledWith(0, 100)

    await userEvent.click(steps)
    expect(screen.getByText('주문 화면')).toBeInTheDocument()
  })

  it('groups the menu into 쇼핑 · 혜택 · 설정 with the right links', async () => {
    renderMy()
    await screen.findByText('임준섭')

    const expectLinks = (group: string, links: [string, string][]) => {
      const section = within(screen.getByRole('region', { name: group }))
      expect(section.getAllByRole('link').map((a) => [a.textContent, a.getAttribute('href')])).toEqual(links)
    }
    expectLinks('쇼핑', [
      ['주문 내역', '/orders'],
      ['찜한 상품', '/wishlist'],
      ['내 리뷰', '/my/reviews'],
    ])
    expectLinks('혜택', [
      ['쿠폰함', '/my/coupons'],
      ['쿠폰 받으러 가기', '/coupons'],
      ['포인트 내역', '/points'],
    ])
    expectLinks('설정', [['배송지 관리', '/addresses']])
  })

  it('logs out through the app bridge after confirming', async () => {
    const logout = vi.fn()
    window.ModuApp = { logout } as unknown as NonNullable<Window['ModuApp']>
    renderMy()
    await screen.findByText('임준섭')

    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: '로그아웃' }))

    expect(logout).toHaveBeenCalledOnce()
  })
})
