import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as reviews from '../api/reviews'
import { review } from './ProductReviewsPage.test'
import ReviewFormPage from './ReviewFormPage'

const target = (over: Partial<reviews.ReviewTarget> = {}): reviews.ReviewTarget => ({
  orderItemId: 10,
  productId: 5,
  productName: '모두 머그컵 세트',
  optionLabel: '',
  imageUrl: null,
  reviewable: true,
  reviewId: null,
  ...over,
})

const renderAt = (path: string) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/reviews/new" element={<ReviewFormPage />} />
        <Route path="/reviews/:id/edit" element={<ReviewFormPage />} />
        <Route path="/products/:id/reviews" element={<p>리뷰 목록 화면</p>} />
        <Route path="/my/reviews" element={<p>내 리뷰 화면</p>} />
      </Routes>
    </MemoryRouter>,
  )

describe('ReviewFormPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('writes a review once a rating and 10+ characters are given', async () => {
    vi.spyOn(reviews, 'getReviewTarget').mockResolvedValue(target())
    const write = vi.spyOn(reviews, 'writeReview').mockResolvedValue(review({ mine: true }))
    renderAt('/reviews/new?orderItemId=10')

    expect(await screen.findByText('모두 머그컵 세트')).toBeInTheDocument()
    const submit = screen.getByRole('button', { name: '등록' })
    expect(submit).toBeDisabled()
    await userEvent.click(screen.getByRole('radio', { name: '5점' }))
    await userEvent.type(screen.getByLabelText('솔직한 리뷰를 남겨 주세요'), '짧아요')
    expect(submit).toBeDisabled()
    await userEvent.type(screen.getByLabelText('솔직한 리뷰를 남겨 주세요'), ' 그래도 튼튼하고 좋아요')
    expect(submit).toBeEnabled()
    await userEvent.click(submit)

    expect(write).toHaveBeenCalledWith(10, 5, '짧아요 그래도 튼튼하고 좋아요')
    expect(await screen.findByText('리뷰 목록 화면')).toBeInTheDocument()
  })

  it('redirects to the edit screen when the order item already has a review', async () => {
    vi.spyOn(reviews, 'getReviewTarget').mockResolvedValue(target({ reviewId: 7 }))
    vi.spyOn(reviews, 'getMyReview').mockResolvedValue(review({ id: 7, mine: true, rating: 3 }))
    renderAt('/reviews/new?orderItemId=10')

    expect(await screen.findByRole('radio', { name: '3점' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByText('리뷰 수정')).toBeInTheDocument()
    expect(screen.getByLabelText('솔직한 리뷰를 남겨 주세요')).toHaveValue('머그컵이 튼튼하고 색이 예뻐요')
  })

  it('shows the server message on 400 and blocks cancelled orders', async () => {
    vi.spyOn(reviews, 'getReviewTarget').mockResolvedValue(target())
    vi.spyOn(reviews, 'writeReview').mockRejectedValue(new ApiError(400, JSON.stringify({ message: '이미 리뷰를 쓴 상품입니다.' })))
    renderAt('/reviews/new?orderItemId=10')

    await userEvent.click(await screen.findByRole('radio', { name: '4점' }))
    await userEvent.type(screen.getByLabelText('솔직한 리뷰를 남겨 주세요'), '열 글자는 넘는 리뷰 내용입니다')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(await screen.findByText('이미 리뷰를 쓴 상품입니다.')).toBeInTheDocument()
  })

  it('deletes from the edit screen after confirmation', async () => {
    vi.spyOn(reviews, 'getMyReview').mockResolvedValue(review({ id: 7, mine: true }))
    const del = vi.spyOn(reviews, 'deleteReview').mockResolvedValue(undefined)
    renderAt('/reviews/7/edit')

    await userEvent.click(await screen.findByRole('button', { name: '리뷰 삭제' }))
    await userEvent.click(screen.getByRole('dialog').querySelector('.actions button:last-child')!)
    expect(del).toHaveBeenCalledWith(7)
    expect(await screen.findByText('내 리뷰 화면')).toBeInTheDocument()
  })
})
