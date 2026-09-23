import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as me from '../api/me'
import * as points from '../api/points'
import MyPage from './MyPage'

const renderMy = () =>
  render(
    <MemoryRouter initialEntries={['/my']}>
      <Routes>
        <Route path="/my" element={<MyPage />} />
      </Routes>
    </MemoryRouter>,
  )

describe('MyPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(me, 'getProfile').mockResolvedValue({ name: '임준섭', email: 'me@modu.local', picture: '' })
  })

  it('shows my points next to the 포인트 row', async () => {
    vi.spyOn(points, 'getMyPoints').mockResolvedValue(1580)
    renderMy()

    expect(await screen.findByText('1,580P')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /포인트/ })).toHaveAttribute('href', '/points')
    expect(screen.getByText('임준섭')).toBeInTheDocument()
  })

  it('keeps the row but no value when the point service is down', async () => {
    vi.spyOn(points, 'getMyPoints').mockRejectedValue(new Error('503'))
    renderMy()

    expect(await screen.findByText('임준섭')).toBeInTheDocument()
    const row = screen.getByRole('link', { name: /포인트/ })
    expect(row.querySelector('.point-value')).toHaveTextContent('')
  })
})
