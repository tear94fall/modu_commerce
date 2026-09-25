import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as catalog from '../api/catalog'
import { CATEGORY_TREE, LocationProbe, page, product } from '../test-fixtures/catalog'
import CategoryPage from './CategoryPage'

const renderPage = (url = '/categories') =>
  render(
    <MemoryRouter initialEntries={['/', url]} initialIndex={1}>
      <Routes>
        <Route path="/" element={<p>홈 화면</p>} />
        <Route path="/categories" element={<CategoryPage />} />
        <Route path="/products" element={<p>목록 화면</p>} />
        <Route path="/search" element={<p>검색 화면</p>} />
      </Routes>
      <LocationProbe />
    </MemoryRouter>,
  )

const rail = () => screen.getByRole('navigation', { name: '대분류' })
const location = () => screen.getByTestId('location').textContent

describe('CategoryPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(catalog, 'getCategories').mockResolvedValue(CATEGORY_TREE)
    vi.spyOn(catalog, 'getProducts').mockResolvedValue(page([product({ name: '인기 냄비' })]))
  })

  it('opens the first root by default with its sub-category grid and popular products', async () => {
    renderPage()

    const pane = await screen.findByRole('region', { name: '생활' })
    expect(within(rail()).getByRole('button', { name: /생활/ })).toHaveAttribute('aria-current', 'true')
    expect(within(pane).getByRole('link', { name: /주방/ })).toHaveAttribute('href', '/products?categoryId=11&title=%EC%A3%BC%EB%B0%A9')
    expect(within(pane).getByRole('link', { name: /욕실/ })).toBeInTheDocument()
    expect(within(pane).getByRole('link', { name: /전체 보기/ })).toHaveAttribute('href', '/products?categoryId=1&title=%EC%83%9D%ED%99%9C')
    expect(within(pane).getByText('🍳')).toBeInTheDocument()

    expect(await within(pane).findByText('인기 상품')).toBeInTheDocument()
    expect(within(pane).getByText('인기 냄비')).toBeInTheDocument()
    expect(catalog.getProducts).toHaveBeenCalledWith({ categoryId: 1, sort: 'popular', size: 8 })
  })

  it('switches the root from the rail, keeps it in the URL with replace', async () => {
    renderPage()
    await screen.findByRole('region', { name: '생활' })

    await userEvent.click(within(rail()).getByRole('button', { name: /문구/ }))

    const pane = screen.getByRole('region', { name: '문구' })
    expect(location()).toBe('/categories?root=2')
    expect(within(rail()).getByRole('button', { name: /문구/ })).toHaveAttribute('aria-current', 'true')
    // 소분류가 없으면 "전체 상품 보기" 한 칸, 아이콘이 없으면 이름 첫 글자.
    expect(within(pane).getByRole('link', { name: /전체 상품 보기/ })).toHaveAttribute('href', '/products?categoryId=2&title=%EB%AC%B8%EA%B5%AC')
    expect(within(pane).getAllByText('문').length).toBeGreaterThan(0)
    await waitFor(() => expect(catalog.getProducts).toHaveBeenCalledWith({ categoryId: 2, sort: 'popular', size: 8 }))

    // replace 라서 뒤로 가면 카테고리 탭 이전 화면이다.
    await userEvent.click(screen.getByRole('button', { name: '이전' }))
    expect(screen.getByText('홈 화면')).toBeInTheDocument()
  })

  it('restores the root from ?root= (coming back from a product list)', async () => {
    renderPage('/categories?root=2')

    expect(await screen.findByRole('region', { name: '문구' })).toBeInTheDocument()
    expect(within(rail()).getByRole('button', { name: /문구/ })).toHaveAttribute('aria-current', 'true')
  })

  it('falls back to the first root for an unknown ?root=', async () => {
    renderPage('/categories?root=999')

    expect(await screen.findByRole('region', { name: '생활' })).toBeInTheDocument()
  })

  it('opens a sub-category product list from the grid', async () => {
    renderPage()
    const pane = await screen.findByRole('region', { name: '생활' })

    await userEvent.click(within(pane).getByRole('link', { name: /욕실/ }))

    expect(screen.getByText('목록 화면')).toBeInTheDocument()
    expect(location()).toBe('/products?categoryId=12&title=%EC%9A%95%EC%8B%A4')
  })

  it('hides the popular row when the products fail to load', async () => {
    vi.spyOn(catalog, 'getProducts').mockRejectedValue(new Error('down'))
    renderPage()

    const pane = await screen.findByRole('region', { name: '생활' })
    await waitFor(() => expect(catalog.getProducts).toHaveBeenCalled())
    expect(within(pane).queryByText('인기 상품')).toBeNull()
    expect(within(pane).getByRole('link', { name: /주방/ })).toBeInTheDocument()
  })

  it('has a search button in the top bar', async () => {
    renderPage()
    await screen.findByRole('region', { name: '생활' })

    await userEvent.click(screen.getByRole('link', { name: '검색' }))
    expect(screen.getByText('검색 화면')).toBeInTheDocument()
  })
})
