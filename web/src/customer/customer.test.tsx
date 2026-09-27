import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import * as customerApi from '../api/customer'
import { customerMe } from '../test-fixtures/customer'
import CustomerLayout from './CustomerProvider'
import { safeNext, welcomeUrl } from './paths'
import RequireCustomer from './RequireCustomer'

const forbidden = () => new Response(JSON.stringify({ message: '모두의 커머스 가입이 필요합니다', code: 'CUSTOMER_REQUIRED' }), { status: 403 })

/** 버튼을 누르면 가입이 필요한 API 를 부르는 화면. */
function Caller({ label, quiet = false }: { label: string; quiet?: boolean }) {
  return (
    <button type="button" onClick={() => api('/api/v1/cart/items', { method: 'POST', body: '{}', quiet }).catch(() => {})}>
      {label}
    </button>
  )
}

function WelcomeProbe() {
  const location = useLocation()
  const navigate = useNavigate()
  return (
    <>
      <p>가입 화면 {location.search}</p>
      <button type="button" onClick={() => navigate(-1)}>
        뒤로
      </button>
    </>
  )
}

/** 홈 → url 순서로 들어온 상태. 뒤로 가면 무엇이 나오는지로 push/replace 를 구분한다. */
const renderAt = (url: string) =>
  render(
    <MemoryRouter initialEntries={['/', url]} initialIndex={1}>
      <Routes>
        <Route element={<CustomerLayout />}>
          <Route path="/" element={<p>홈 화면</p>} />
          <Route path="/welcome" element={<WelcomeProbe />} />
          <Route path="/products/:id" element={<Caller label="장바구니 담기" />} />
          <Route path="/quiet" element={<Caller label="뱃지 세기" quiet />} />
          <Route element={<RequireCustomer />}>
            <Route path="/cart" element={<p>장바구니 화면</p>} />
          </Route>
        </Route>
      </Routes>
    </MemoryRouter>,
  )

describe('customer gate', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })
  afterEach(() => vi.restoreAllMocks())

  it('sends any 403 CUSTOMER_REQUIRED to /welcome with the current path as next', async () => {
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(customerMe())
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(forbidden())
    renderAt('/products/5')

    await userEvent.click(await screen.findByRole('button', { name: '장바구니 담기' }))

    expect(await screen.findByText('가입 화면 ?next=%2Fproducts%2F5')).toBeInTheDocument()
    // 둘러보기 화면은 남겨 둔다(push): 뒤로 가면 상품 화면.
    await userEvent.click(screen.getByRole('button', { name: '뒤로' }))
    expect(await screen.findByRole('button', { name: '장바구니 담기' })).toBeInTheDocument()
  })

  it('does not redirect for quiet background requests', async () => {
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(customerMe())
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(forbidden())
    renderAt('/quiet')

    await userEvent.click(await screen.findByRole('button', { name: '뱃지 세기' }))

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(screen.getByRole('button', { name: '뱃지 세기' })).toBeInTheDocument()
  })

  it('redirects a protected route to /welcome (replacing it) when not a customer', async () => {
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(null)
    renderAt('/cart')

    expect(await screen.findByText('가입 화면 ?next=%2Fcart')).toBeInTheDocument()
    // 막힌 화면은 바꿔 끼웠으므로 뒤로 가면 다시 막히지 않고 홈.
    await userEvent.click(screen.getByRole('button', { name: '뒤로' }))
    expect(await screen.findByText('홈 화면')).toBeInTheDocument()
  })

  it('keeps browsing routes open before joining', async () => {
    const load = vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(null)
    renderAt('/products/5')

    await vi.waitFor(() => expect(load).toHaveBeenCalled())
    expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeInTheDocument()
  })

  it('lets customers into protected routes', async () => {
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(customerMe())
    renderAt('/cart')

    expect(await screen.findByText('장바구니 화면')).toBeInTheDocument()
  })

  it('does not bounce while already on /welcome', async () => {
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(null)
    renderAt('/welcome?next=%2Fcart')

    expect(await screen.findByText('가입 화면 ?next=%2Fcart')).toBeInTheDocument()
  })
})

describe('safeNext', () => {
  it('accepts known in-app paths with their query', () => {
    expect(safeNext('/checkout?cartItemIds=1,2')).toBe('/checkout?cartItemIds=1,2')
    expect(safeNext('/products/5')).toBe('/products/5')
    expect(safeNext('/')).toBe('/')
  })

  it('falls back to home for anything else', () => {
    for (const bad of [null, '', 'https://evil.example', '//evil.example', '/\\evil', '/welcome?next=/cart', '/login', '/unknown', 'cart', '/cart#x', '/cart?x=<script>']) {
      expect(safeNext(bad)).toBe('/')
    }
  })

  it('builds the welcome url without a next for home', () => {
    expect(welcomeUrl('/')).toBe('/welcome')
    expect(welcomeUrl('/orders/7')).toBe('/welcome?next=%2Forders%2F7')
  })
})
