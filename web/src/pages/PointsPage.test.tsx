import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as points from '../api/points'
import PointsPage from './PointsPage'

const tx = (over: Partial<points.PointTransaction> = {}): points.PointTransaction => ({
  id: 1,
  type: 'EARN',
  amount: 100,
  balanceAfter: 100,
  ruleCode: 'SIGNUP',
  refId: null,
  memo: null,
  createdDate: '2026-09-23T01:00:00',
  ...over,
})
const page = (content: points.PointTransaction[], number = 0, totalPages = 1) => ({ content, totalElements: content.length, totalPages, number })

const renderPoints = () =>
  render(
    <MemoryRouter initialEntries={['/points']}>
      <Routes>
        <Route path="/points" element={<PointsPage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('PointsPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows the balance and the ledger with labels and signs', async () => {
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(580)
    vi.spyOn(points, 'getPointHistory').mockResolvedValue(
      page([
        tx({ id: 3, type: 'ADJUST', amount: 500, balanceAfter: 580, ruleCode: null, memo: '이벤트 보상' }),
        tx({ id: 2, type: 'SPEND', amount: -30, balanceAfter: 80, ruleCode: null, refId: 'order:1', memo: '주문 할인' }),
        tx({ id: 1, ruleCode: 'DAILY_CHECKIN', amount: 10, balanceAfter: 110 }),
      ]),
    )
    renderPoints()

    expect(await screen.findByText('580P', { selector: '.balance' })).toBeInTheDocument()
    expect(screen.getByText('이벤트 보상')).toBeInTheDocument()
    expect(screen.getByText('+500P')).toBeInTheDocument()
    expect(screen.getByText('주문 할인')).toBeInTheDocument()
    expect(screen.getByText('-30P')).toHaveClass('minus')
    expect(screen.getByText('출석 체크')).toBeInTheDocument()
    expect(screen.getByText('110P')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument()
  })

  it('loads the next page on 더 보기', async () => {
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(20)
    const history = vi
      .spyOn(points, 'getPointHistory')
      .mockResolvedValueOnce(page([tx({ id: 2, ruleCode: 'FIRST_CHAT', amount: 20, balanceAfter: 20 })], 0, 2))
      .mockResolvedValueOnce(page([tx({ id: 1, ruleCode: 'SIGNUP', amount: 100, balanceAfter: 100 })], 1, 2))
    renderPoints()

    await userEvent.click(await screen.findByRole('button', { name: '더 보기' }))
    expect(await screen.findByText('가입 축하')).toBeInTheDocument()
    expect(history).toHaveBeenLastCalledWith(1)
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument()
  })

  it('shows an error box with retry when the service is down', async () => {
    vi.spyOn(points, 'getMyPoints').mockRejectedValue(new Error('503'))
    vi.spyOn(points, 'getPointHistory').mockRejectedValue(new Error('503'))
    renderPoints()

    expect(await screen.findByText('포인트를 불러오지 못했습니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
