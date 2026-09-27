import { act, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as push from '../api/push'
import NotificationButton from './NotificationButton'

const renderButton = () =>
  render(
    <MemoryRouter>
      <NotificationButton />
    </MemoryRouter>,
  )

describe('NotificationButton', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows the unread count', async () => {
    vi.spyOn(push, 'getUnreadNotificationCount').mockResolvedValue(3)
    renderButton()
    const link = screen.getByRole('link', { name: '알림' })
    expect(link).toHaveAttribute('href', '/notifications')
    expect(await screen.findByText('3')).toHaveClass('badge')
  })

  it('caps the badge at 9+', async () => {
    vi.spyOn(push, 'getUnreadNotificationCount').mockResolvedValue(12)
    renderButton()
    expect(await screen.findByText('9+')).toBeInTheDocument()
  })

  it('hides the badge at 0', async () => {
    const count = vi.spyOn(push, 'getUnreadNotificationCount').mockResolvedValue(0)
    renderButton()
    await act(async () => {})
    expect(count).toHaveBeenCalledTimes(1)
    expect(document.querySelector('.badge')).toBeNull()
  })

  it('refreshes when the page becomes visible again', async () => {
    const count = vi.spyOn(push, 'getUnreadNotificationCount').mockResolvedValueOnce(0).mockResolvedValueOnce(5)
    renderButton()
    await act(async () => {})
    expect(document.querySelector('.badge')).toBeNull()

    await act(async () => document.dispatchEvent(new Event('visibilitychange')))
    expect(count).toHaveBeenCalledTimes(2)
    expect(await screen.findByText('5')).toBeInTheDocument()
  })
})
