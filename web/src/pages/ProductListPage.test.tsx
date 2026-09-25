import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as catalog from '../api/catalog'
import { CATEGORY_TREE, LocationProbe, page, product } from '../test-fixtures/catalog'
import ProductListPage from './ProductListPage'

const renderList = (url: string) =>
  render(
    <MemoryRouter initialEntries={['/categories', url]} initialIndex={1}>
      <Routes>
        <Route path="/categories" element={<p>카테고리 화면</p>} />
        <Route path="/products" element={<ProductListPage />} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  )

const location = () => new URL(screen.getByTestId('location').textContent ?? '', 'http://x')
const roots = () => screen.getByRole('tablist', { name: '대분류' })
const subs = (root: string) => screen.getByRole('tablist', { name: `${root} 소분류` })

describe('ProductListPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(catalog, 'getCategories').mockResolvedValue(CATEGORY_TREE)
    vi.spyOn(catalog, 'getProducts').mockImplementation(async ({ categoryId }) => page([product({ id: categoryId ?? 0, name: `상품-${categoryId ?? '없음'}` })]))
  })

  it('shows root pills and sub-category chips for the current category', async () => {
    renderList('/products?categoryId=11&title=%EC%A3%BC%EB%B0%A9')

    expect(await screen.findByText('생활 › 주방')).toBeInTheDocument()
    expect(within(roots()).getByRole('tab', { name: /생활/ })).toHaveAttribute('aria-selected', 'true')
    expect(within(roots()).getByRole('tab', { name: /문구/ })).toHaveAttribute('aria-selected', 'false')
    const chips = within(subs('생활')).getAllByRole('tab')
    expect(chips.map((c) => c.textContent)).toEqual(['전체', '주방', '욕실'])
    expect(within(subs('생활')).getByRole('tab', { name: '주방' })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('상품-11')).toBeInTheDocument()
  })

  it('switches the sub-category in place with replace and keeps the sort', async () => {
    renderList('/products?categoryId=11&title=%EC%A3%BC%EB%B0%A9&sort=popular')
    await screen.findByText('상품-11')

    await userEvent.click(within(subs('생활')).getByRole('tab', { name: '욕실' }))

    expect(await screen.findByText('상품-12')).toBeInTheDocument()
    expect(screen.getByText('생활 › 욕실')).toBeInTheDocument()
    expect(catalog.getProducts).toHaveBeenLastCalledWith({ categoryId: 12, sort: 'popular', page: 0 })
    expect(location().searchParams.get('categoryId')).toBe('12')
    expect(location().searchParams.get('sort')).toBe('popular')
    expect(screen.getByRole('radio', { name: '인기순' })).toHaveAttribute('aria-checked', 'true')

    // "전체" 는 대분류 자신.
    await userEvent.click(within(subs('생활')).getByRole('tab', { name: '전체' }))
    expect(await screen.findByText('상품-1')).toBeInTheDocument()
    expect(screen.getByText('생활', { selector: '.title' })).toBeInTheDocument()

    // replace 라서 뒤로 가면 목록에 들어오기 전 화면.
    await userEvent.click(screen.getByRole('button', { name: '이전' }))
    expect(screen.getByText('카테고리 화면')).toBeInTheDocument()
  })

  it('switches the root from the pill row', async () => {
    renderList('/products?categoryId=11&title=%EC%A3%BC%EB%B0%A9')
    await screen.findByText('상품-11')

    await userEvent.click(within(roots()).getByRole('tab', { name: /문구/ }))

    expect(await screen.findByText('상품-2')).toBeInTheDocument()
    expect(location().searchParams.get('categoryId')).toBe('2')
    expect(within(subs('문구')).getAllByRole('tab').map((c) => c.textContent)).toEqual(['전체'])
    expect(within(roots()).getByRole('tab', { name: /문구/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('keeps the category when the sort changes', async () => {
    renderList('/products?categoryId=12&title=%EC%9A%95%EC%8B%A4')
    await screen.findByText('상품-12')

    await userEvent.click(screen.getByRole('radio', { name: '낮은 가격순' }))

    await waitFor(() => expect(catalog.getProducts).toHaveBeenLastCalledWith({ categoryId: 12, sort: 'priceAsc', page: 0 }))
    expect(location().searchParams.get('categoryId')).toBe('12')
    expect(location().searchParams.get('sort')).toBe('priceAsc')
  })

  it('shows a friendly empty state with a way to the whole root', async () => {
    vi.spyOn(catalog, 'getProducts').mockImplementation(async ({ categoryId }) => (categoryId === 12 ? page([]) : page([product({ name: '생활 상품' })])))
    renderList('/products?categoryId=12&title=%EC%9A%95%EC%8B%A4')

    expect(await screen.findByText('아직 상품이 없어요')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '생활 전체 보기' }))

    expect(await screen.findByText('생활 상품')).toBeInTheDocument()
    expect(location().searchParams.get('categoryId')).toBe('1')
  })

  it('keeps the plain layout for lists without a category', async () => {
    renderList('/products?sort=popular&title=%EC%9D%B8%EA%B8%B0%20%EC%83%81%ED%92%88')

    expect(await screen.findByText('상품-없음')).toBeInTheDocument()
    expect(screen.getByText('인기 상품')).toBeInTheDocument()
    expect(screen.queryByRole('tablist')).toBeNull()
    expect(catalog.getCategories).not.toHaveBeenCalled()
    expect(catalog.getProducts).toHaveBeenCalledWith({ categoryId: undefined, sort: 'popular', page: 0 })
  })

  it('falls back to the title param when categories fail to load', async () => {
    vi.spyOn(catalog, 'getCategories').mockRejectedValue(new Error('down'))
    renderList('/products?categoryId=11&title=%EC%A3%BC%EB%B0%A9')

    expect(await screen.findByText('상품-11')).toBeInTheDocument()
    expect(screen.getByText('주방', { selector: '.title' })).toBeInTheDocument()
    expect(screen.queryByRole('tablist')).toBeNull()
  })
})
