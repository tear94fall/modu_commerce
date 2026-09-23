import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as catalog from '../api/catalog'
import HomePage from './HomePage'

const product = (over: Partial<catalog.ProductSummary> = {}): catalog.ProductSummary => ({
  id: 1,
  name: '모두 베이직 티셔츠',
  imageUrl: null,
  price: 12000,
  listPrice: 18000,
  discountRate: 33,
  soldOut: false,
  wished: false,
  reviewCount: 0,
  ratingAverage: 0,
  ...over,
})
const page = (content: catalog.ProductSummary[]): catalog.Page<catalog.ProductSummary> => ({ content, totalElements: content.length, totalPages: 1, number: 0 })

const renderHome = () =>
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/products" element={<p>목록 화면</p>} />
        <Route path="/products/:id" element={<p>상세 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('HomePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(catalog, 'getCategories').mockResolvedValue([{ id: 1, name: '패션', children: [] }])
  })

  it('shows category chips, rows and the grid with discounted prices', async () => {
    vi.spyOn(catalog, 'getProducts').mockImplementation(async ({ sort, size }) => {
      if (size === 10) return page([product({ id: sort === 'popular' ? 2 : 1, name: sort === 'popular' ? '인기 머그' : '새 티셔츠' })])
      return page([product({ id: 3, name: '격자 상품' })])
    })
    renderHome()

    expect(await screen.findByText('패션')).toBeInTheDocument()
    expect(await screen.findByText('새 티셔츠')).toBeInTheDocument()
    expect(screen.getByText('인기 머그')).toBeInTheDocument()
    expect(screen.getByText('격자 상품')).toBeInTheDocument()
    expect(screen.getAllByText('12,000원').length).toBe(3)
    expect(screen.getAllByText('33%').length).toBe(3)
  })

  it('opens the category list from a chip and the detail from a card', async () => {
    vi.spyOn(catalog, 'getProducts').mockResolvedValue(page([product()]))
    renderHome()

    await userEvent.click(await screen.findByText('패션'))
    expect(screen.getByText('목록 화면')).toBeInTheDocument()
  })

  it('toggles a wish optimistically and reverts when the server fails', async () => {
    vi.spyOn(catalog, 'getProducts').mockImplementation(async ({ size }) => (size === 10 ? page([]) : page([product()])))
    const setWish = vi.spyOn(catalog, 'setWish').mockRejectedValue(new Error('down'))
    renderHome()

    const button = await screen.findByRole('button', { name: '찜' })
    await userEvent.click(button)
    expect(setWish).toHaveBeenCalledWith(1, true)
    expect(await screen.findByText('찜을 바꾸지 못했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '찜' })).toHaveAttribute('aria-pressed', 'false')
  })
})
