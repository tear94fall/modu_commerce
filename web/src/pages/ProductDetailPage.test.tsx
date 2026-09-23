import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as cart from '../api/cart'
import * as catalog from '../api/catalog'
import * as reviews from '../api/reviews'
import { ApiError } from '../api/client'
import ProductDetailPage from './ProductDetailPage'

const detail = (over: Partial<catalog.ProductDetail> = {}): catalog.ProductDetail => ({
  id: 5,
  name: '모두 스티커 팩',
  description: '스티커 30장',
  detail: '상세 설명입니다',
  images: ['https://img/1.png'],
  price: 5000,
  listPrice: null,
  discountRate: 0,
  soldOut: false,
  wished: false,
  wishCount: 2,
  reviewCount: 0,
  ratingAverage: 0,
  categoryPath: ['문구', '노트·데스크'],
  optionGroups: [
    { id: 1, name: '색상', values: [{ id: 11, name: '블랙' }, { id: 12, name: '그린' }] },
    { id: 2, name: '크기', values: [{ id: 21, name: '10' }, { id: 22, name: '20' }] },
  ],
  skus: [
    { id: 100, optionValueIds: [11, 21], optionLabel: '블랙 / 10', extraPrice: 0, stock: 3 },
    { id: 101, optionValueIds: [12, 22], optionLabel: '그린 / 20', extraPrice: 100, stock: 0 },
  ],
  ...over,
})

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/products/5']}>
      <Routes>
        <Route path="/products/:id" element={<ProductDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('ProductDetailPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(reviews, 'getProductReviews').mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0 })
  })

  it('shows the product and adds the chosen SKU to the cart', async () => {
    vi.spyOn(catalog, 'getProduct').mockResolvedValue(detail())
    const add = vi.spyOn(cart, 'addCartItem').mockResolvedValue({} as cart.CartItem)
    renderPage()

    expect(await screen.findByRole('heading', { name: '모두 스티커 팩' })).toBeInTheDocument()
    expect(screen.getByText('문구 › 노트·데스크')).toBeInTheDocument()
    expect(screen.getByText('상세 설명입니다')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '옵션 선택' }))
    expect(screen.getByText('옵션을 모두 골라 주세요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '20' })).toBeDisabled() // 그린/20 재고 0

    await userEvent.click(screen.getByRole('button', { name: '블랙' }))
    await userEvent.click(screen.getByRole('button', { name: '10' }))
    expect(screen.getByText('블랙 / 10')).toBeInTheDocument()
    expect(screen.getByText('남은 수량 3')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '수량 늘리기' }))
    await userEvent.click(screen.getByRole('button', { name: '수량 늘리기' }))
    await userEvent.click(screen.getByRole('button', { name: '수량 늘리기' })) // 재고 3 에서 멈춘다
    expect(screen.getByLabelText('수량')).toHaveTextContent('3')
    expect(screen.getByText('15,000원')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '장바구니 담기' }))
    expect(add).toHaveBeenCalledWith(100, 3)
    expect(await screen.findByText('장바구니에 담았습니다')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows the server message when adding fails with 400', async () => {
    vi.spyOn(catalog, 'getProduct').mockResolvedValue(detail({ optionGroups: [], skus: [{ id: 7, optionValueIds: [], optionLabel: '', extraPrice: 0, stock: 1 }] }))
    vi.spyOn(cart, 'addCartItem').mockRejectedValue(new ApiError(400, JSON.stringify({ message: '재고가 부족합니다' })))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '옵션 선택' }))
    await userEvent.click(screen.getByRole('button', { name: '장바구니 담기' }))
    expect(await screen.findByText('재고가 부족합니다')).toBeInTheDocument()
  })

  it('toggles the wish with its count', async () => {
    vi.spyOn(catalog, 'getProduct').mockResolvedValue(detail())
    const setWish = vi.spyOn(catalog, 'setWish').mockResolvedValue(undefined)
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '찜' }))
    expect(setWish).toHaveBeenCalledWith(5, true)
    expect(screen.getByRole('button', { name: '찜 해제' })).toHaveTextContent('3')
  })

  it('tells when the product is gone', async () => {
    vi.spyOn(catalog, 'getProduct').mockRejectedValue(new ApiError(404, ''))
    renderPage()
    expect(await screen.findByText('상품이 없거나 판매가 끝났습니다.')).toBeInTheDocument()
  })
})
