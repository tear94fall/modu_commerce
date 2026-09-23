import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as reviews from '../api/reviews'
import MyReviewsPage from './MyReviewsPage'
import { review } from './ProductReviewsPage.test'

const page = (content: reviews.Review[]) => ({ content, totalElements: content.length, totalPages: 1, number: 0 })

describe('MyReviewsPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('lists my reviews with the hidden notice and deletes one after confirmation', async () => {
    vi.spyOn(reviews, 'getMyReviews').mockResolvedValue(
      page([review({ mine: true, authorName: '임준섭' }), review({ id: 2, mine: true, content: '숨겨진 두 번째 리뷰입니다', hidden: true, hiddenReason: '광고성 내용' })]),
    )
    const del = vi.spyOn(reviews, 'deleteReview').mockResolvedValue(undefined)
    render(
      <MemoryRouter initialEntries={['/my/reviews']}>
        <Routes>
          <Route path="/my/reviews" element={<MyReviewsPage />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText('머그컵이 튼튼하고 색이 예뻐요')).toBeInTheDocument()
    expect(screen.getByText('관리자가 숨긴 리뷰입니다 · 광고성 내용')).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: '수정' })[0]).toHaveAttribute('href', '/reviews/1/edit')

    await userEvent.click(screen.getAllByRole('button', { name: '삭제' })[1])
    await userEvent.click(screen.getByRole('dialog').querySelector('.actions button:last-child')!)
    expect(del).toHaveBeenCalledWith(2)
    expect(screen.queryByText('숨겨진 두 번째 리뷰입니다')).not.toBeInTheDocument()
    expect(await screen.findByText('리뷰를 삭제했습니다')).toBeInTheDocument()
  })
})
