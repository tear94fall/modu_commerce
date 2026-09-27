import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as push from '../api/push'
import type { ModuAppBridge } from '../bridge/app'
import { ModuWebBridge } from '../bridge/web'
import { formatDate } from '../util/format'
import NotificationSettingsPage from './NotificationSettingsPage'

const Where = () => <p data-testid="where">{useLocation().pathname}</p>

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/settings/notifications']}>
      <ModuWebBridge />
      <Routes>
        <Route path="/settings/notifications" element={<NotificationSettingsPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )

const consent = (c: Partial<push.PushConsent> = {}): push.PushConsent => ({ marketing: false, marketingUpdatedAt: null, night: false, nightUpdatedAt: null, ...c })

const fakeBridge = (permission: 'granted' | 'denied' | 'default') => {
  const b = {
    getAccessToken: () => 'token',
    refreshAccessToken: () => '',
    onSessionExpired: vi.fn(),
    logout: vi.fn(),
    getProfile: () => '{}',
    getNotificationPermission: vi.fn(() => permission),
    requestNotificationPermission: vi.fn(),
    openNotificationSettings: vi.fn(),
  } satisfies ModuAppBridge
  window.ModuApp = b
  return b
}

describe('NotificationSettingsPage', () => {
  beforeEach(() => vi.restoreAllMocks())
  afterEach(() => {
    delete window.ModuApp
  })

  it('shows "설정 안 함" when never set and the outside-the-app note in a browser; night is disabled while marketing is off', async () => {
    vi.spyOn(push, 'getPushConsent').mockResolvedValue(consent())
    renderPage()

    expect(await screen.findByText('알림은 모두의 커머스 앱에서 받을 수 있어요')).toBeInTheDocument()
    expect(screen.getAllByText('설정 안 함')).toHaveLength(2)
    expect(screen.getByRole('switch', { name: '야간 알림 (21시~08시)' })).toBeDisabled()
  })

  it('turning marketing on in the app asks OS permission first, saves consent and shows the dated toast', async () => {
    const b = fakeBridge('default')
    vi.spyOn(push, 'getPushConsent').mockResolvedValue(consent())
    const update = vi.spyOn(push, 'updatePushConsent').mockResolvedValue(consent({ marketing: true, marketingUpdatedAt: '2026-09-27T03:00:00' }))
    renderPage()

    await userEvent.click(await screen.findByRole('switch', { name: '혜택·이벤트 알림' }))
    expect(b.requestNotificationPermission).toHaveBeenCalled()
    expect(update).not.toHaveBeenCalled()

    b.getNotificationPermission.mockReturnValue('denied')
    await act(async () => window.ModuWeb!.onNotificationPermission('denied'))

    expect(update).toHaveBeenCalledWith({ marketing: true, night: false })
    const day = formatDate('2026-09-27T03:00:00')
    expect(await screen.findByText(`모두의 커머스 · ${day} 혜택·이벤트 알림 수신에 동의했습니다`)).toBeInTheDocument()
    expect(screen.getByText(`${day} 수신 동의`)).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('휴대폰 설정에서 알림이 꺼져 있어요')
    await userEvent.click(screen.getByRole('button', { name: '설정 열기' }))
    expect(b.openNotificationSettings).toHaveBeenCalled()
  })

  it('turning marketing off also turns night off', async () => {
    fakeBridge('granted')
    vi.spyOn(push, 'getPushConsent').mockResolvedValue(consent({ marketing: true, marketingUpdatedAt: '2026-09-01T00:00:00', night: true, nightUpdatedAt: '2026-09-01T00:00:00' }))
    const update = vi
      .spyOn(push, 'updatePushConsent')
      .mockResolvedValue(consent({ marketing: false, marketingUpdatedAt: '2026-09-27T03:00:00', night: false, nightUpdatedAt: '2026-09-27T03:00:00' }))
    renderPage()

    await userEvent.click(await screen.findByRole('switch', { name: '혜택·이벤트 알림' }))
    expect(update).toHaveBeenCalledWith({ marketing: false, night: false })
    expect(await screen.findByText(/혜택·이벤트 알림 수신에 거부했습니다$/)).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('ModuWeb.navigate follows allow-listed paths only', async () => {
    vi.spyOn(push, 'getPushConsent').mockResolvedValue(consent())
    renderPage()
    await screen.findAllByText('설정 안 함')

    act(() => window.ModuWeb!.navigate('https://evil.example/'))
    act(() => window.ModuWeb!.navigate('/orders'))
    expect(screen.queryByTestId('where')).not.toBeInTheDocument()

    act(() => window.ModuWeb!.navigate('/products/42'))
    expect(screen.getByTestId('where')).toHaveTextContent('/products/42')
  })
})
