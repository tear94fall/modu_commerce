import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as customerApi from '../api/customer'
import CustomerLayout from '../customer/CustomerProvider'
import { customerMe, GOLD, SILVER, tiers } from '../test-fixtures/customer'
import MembershipPage from './MembershipPage'

const renderMembership = () =>
  render(
    <MemoryRouter initialEntries={['/membership']}>
      <Routes>
        <Route element={<CustomerLayout />}>
          <Route path="/membership" element={<MembershipPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )

describe('MembershipPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(customerMe({ tier: GOLD, rolling: { amount: 184000, expectedTier: SILVER, nextTier: GOLD, amountToNext: 116000 } }))
  })

  it('lists every tier and highlights the current one', async () => {
    vi.spyOn(customerApi, 'getTiers').mockResolvedValue(tiers())
    renderMembership()

    const list = await screen.findByRole('list', { name: '등급별 혜택' })
    const cards = within(list).getAllByRole('listitem')
    expect(cards.map((c) => c.getAttribute('aria-label'))).toEqual(['웰컴', '실버', '골드', 'VIP'])
    const current = cards.filter((c) => c.getAttribute('aria-current') === 'true')
    expect(current.map((c) => c.getAttribute('aria-label'))).toEqual(['골드'])
    expect(within(current[0]).getByText('내 등급')).toBeInTheDocument()
    expect(within(current[0]).getByText('300,000원 이상')).toBeInTheDocument()
    expect(within(current[0]).getByText('3%')).toBeInTheDocument()
    expect(within(current[0]).getByText('골드 5% (5%)')).toBeInTheDocument()

    expect(within(cards[0]).getByText('가입하면 바로')).toBeInTheDocument()
    expect(within(cards[0]).getByText('없음')).toBeInTheDocument()
    expect(within(cards[1]).getByText('다음 달 예상')).toBeInTheDocument()
    expect(within(cards[3]).getByText('VIP 1만원 (10,000원), VIP 무료배송')).toBeInTheDocument()
  })

  it('shows my basis, the expected tier for next month and the rule', async () => {
    vi.spyOn(customerApi, 'getTiers').mockResolvedValue(tiers())
    renderMembership()

    const mine = await screen.findByRole('region', { name: '내 등급' })
    expect(within(mine).getByText('2026.03 ~ 2026.08 배송 완료 320,000원 기준')).toBeInTheDocument()
    expect(within(mine).getByText('실버')).toBeInTheDocument()
    expect(within(mine).getByText('최근 6개월 구매 184,000원 · 골드까지 116,000원')).toBeInTheDocument()
    expect(screen.getByText('매월 1일, 지난 6개월 배송 완료 금액으로 정해져요. 포인트로 낸 금액은 빠져요.')).toBeInTheDocument()
  })

  it('retries when the tiers fail to load', async () => {
    vi.spyOn(customerApi, 'getTiers').mockRejectedValueOnce(new Error('503')).mockResolvedValue(tiers())
    renderMembership()

    await userEvent.click(await screen.findByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('list', { name: '등급별 혜택' })).toBeInTheDocument()
  })
})
