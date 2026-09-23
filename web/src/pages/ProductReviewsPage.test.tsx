import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as reviews from '../api/reviews'
import ProductReviewsPage from './ProductReviewsPage'

export const review = (over: Partial<reviews.Review> = {}): reviews.Review => ({
  id: 1,
  productId: 5,
  productName: '모두 머그컵 세트',
  optionLabel: '',
  productImageUrl: null,
  orderItemId: 10,
  userId: null,
  authorName: '임*섭',
  authorEmail: null,
  rating: 5,
  content: '머그컵이 튼튼하고 색이 예뻐요',
  hidden: false,
  hiddenReason: null,
  mine: false,
  createdAt: '2026-09-23T01:00:00',
  updatedAt: null,
  ...over,
})
const page = (content: reviews.Review[], number = 0, totalPages = 1) => ({ content, totalElements: content.length, totalPages, number })
const summary: reviews.ReviewSummary = {
  count: 2,
  average: 4.5,
  distribution: [
    { rating: 5, count: 1 },
    { rating: 4, count: 1 },
    { rating: 3, count: 0 },
    { rating: 2, count: 0 },
    { rating: 1, count: 0 },
  ],
}

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/products/5/reviews']}>
      <Routes>
        <Route path="/products/:id/reviews" element={<ProductReviewsPage />} />
        <Route path="/reviews/:id/edit" element={<p>수정 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('ProductReviewsPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows the summary, the list with masked names, and an edit link only on mine', async () => {
    vi.spyOn(reviews, 'getReviewSummary').mockResolvedValue(summary)
    vi.spyOn(reviews, 'getProductReviews').mockResolvedValue(page([review(), review({ id: 2, rating: 4, authorName: '임준섭', mine: true, content: '두 번째 리뷰는 제 것입니다' })]))
    renderPage()

    expect(await screen.findByText('4.5')).toBeInTheDocument()
    expect(screen.getByText('리뷰 2개')).toBeInTheDocument()
    expect(screen.getByText('임*섭')).toBeInTheDocument()
    expect(screen.getByText('머그컵이 튼튼하고 색이 예뻐요')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: '수정' })).toHaveLength(1)
    expect(screen.getByRole('link', { name: '수정' })).toHaveAttribute('href', '/reviews/2/edit')
  })

  it('re-fetches with the chosen sort and loads more pages', async () => {
    vi.spyOn(reviews, 'getReviewSummary').mockResolvedValue(summary)
    const list = vi
      .spyOn(reviews, 'getProductReviews')
      .mockResolvedValueOnce(page([review()], 0, 2))
      .mockResolvedValueOnce(page([review({ id: 3, content: '두 번째 페이지의 리뷰입니다' })], 1, 2))
      .mockResolvedValueOnce(page([review({ id: 4, rating: 1, content: '별점 낮은순 첫 리뷰입니다' })]))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '더 보기' }))
    expect(await screen.findByText('두 번째 페이지의 리뷰입니다')).toBeInTheDocument()
    expect(list).toHaveBeenLastCalledWith(5, 1, 'latest')

    await userEvent.click(screen.getByRole('radio', { name: '별점 낮은순' }))
    expect(await screen.findByText('별점 낮은순 첫 리뷰입니다')).toBeInTheDocument()
    expect(list).toHaveBeenLastCalledWith(5, 0, 'low')
  })

  it('shows an empty message when there are no reviews', async () => {
    vi.spyOn(reviews, 'getReviewSummary').mockResolvedValue({ ...summary, count: 0, average: 0 })
    vi.spyOn(reviews, 'getProductReviews').mockResolvedValue(page([]))
    renderPage()

    expect(await screen.findByText(/아직 리뷰가 없습니다/)).toBeInTheDocument()
  })
})
