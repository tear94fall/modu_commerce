import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as push from '../api/push'
import { formatRelativeTime } from '../util/format'
import NotificationsPage from './NotificationsPage'

const Where = () => <p data-testid="where">{useLocation().pathname}</p>

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/notifications']}>
      <Routes>
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )

/** 지금으로부터 [minutes] 분 전의 서버식 UTC 시각(시간대 표시 없음). */
const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString().replace('Z', '')

const item = (over: Partial<push.NotificationItem> = {}): push.NotificationItem => ({
  id: 1,
  campaignId: 10,
  title: '가을 특가',
  body: '오늘만 최대 50% 할인',
  imageUrl: null,
  path: '/products/42',
  receivedAt: minutesAgo(5),
  read: false,
  ...over,
})

const page = (content: push.NotificationItem[], number = 0, totalPages = 1) => ({ content, totalElements: content.length, totalPages, number, size: 20 })

const row = (title: string) => screen.getByText(title).closest('button')!

describe('NotificationsPage', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('renders unread styling, the ad tag, relative time and a thumbnail only when there is an image', async () => {
    vi.spyOn(push, 'getNotifications').mockResolvedValue(
      page([item({ id: 1, title: '새 알림', receivedAt: minutesAgo(5), imageUrl: 'https://img/1.jpg' }), item({ id: 2, title: '읽은 알림', read: true, receivedAt: minutesAgo(180) })]),
    )
    renderPage()

    expect(await screen.findByText('새 알림')).toBeInTheDocument()
    expect(row('새 알림')).toHaveClass('unread')
    expect(row('읽은 알림')).not.toHaveClass('unread')
    expect(within(row('새 알림')).getByText('5분 전')).toBeInTheDocument()
    expect(within(row('읽은 알림')).getByText('3시간 전')).toBeInTheDocument()
    expect(within(row('새 알림')).getByRole('img', { name: '안 읽음' })).toBeInTheDocument()
    expect(within(row('읽은 알림')).queryByRole('img', { name: '안 읽음' })).toBeNull()
    expect(row('새 알림').querySelector('img.thumb')).toHaveAttribute('src', 'https://img/1.jpg')
    expect(row('읽은 알림').querySelector('img.thumb')).toBeNull()
    expect(screen.getAllByText('광고')).toHaveLength(2)
    expect(screen.getByText('최근 30일 알림만 보여요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '모두 읽음' })).toBeInTheDocument()
  })

  it('tap marks read and navigates to the allow-listed path', async () => {
    vi.spyOn(push, 'getNotifications').mockResolvedValue(page([item()]))
    const read = vi.spyOn(push, 'markNotificationRead').mockResolvedValue(undefined)
    renderPage()

    await userEvent.click(await screen.findByText('가을 특가'))
    expect(read).toHaveBeenCalledWith(1)
    expect(screen.getByTestId('where')).toHaveTextContent('/products/42')
  })

  it('tap on a path outside the allow-list only marks read', async () => {
    vi.spyOn(push, 'getNotifications').mockResolvedValue(page([item({ path: 'https://evil.example/' })]))
    const read = vi.spyOn(push, 'markNotificationRead').mockResolvedValue(undefined)
    renderPage()

    await userEvent.click(await screen.findByText('가을 특가'))
    expect(read).toHaveBeenCalledWith(1)
    expect(screen.queryByTestId('where')).toBeNull()
    expect(row('가을 특가')).not.toHaveClass('unread')
  })

  it('read-all clears unread and hides the button', async () => {
    vi.spyOn(push, 'getNotifications').mockResolvedValue(page([item({ id: 1, title: 'A' }), item({ id: 2, title: 'B' })]))
    const readAll = vi.spyOn(push, 'markAllNotificationsRead').mockResolvedValue(undefined)
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '모두 읽음' }))
    expect(readAll).toHaveBeenCalled()
    expect(row('A')).not.toHaveClass('unread')
    expect(row('B')).not.toHaveClass('unread')
    expect(screen.queryByRole('button', { name: '모두 읽음' })).toBeNull()
  })

  it('shows the empty state without the read-all button', async () => {
    vi.spyOn(push, 'getNotifications').mockResolvedValue(page([]))
    renderPage()

    expect(await screen.findByText('받은 알림이 없어요')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '모두 읽음' })).toBeNull()
  })

  it('loads the next page with 더 보기', async () => {
    const get = vi
      .spyOn(push, 'getNotifications')
      .mockResolvedValueOnce(page([item({ id: 1, title: '첫 장' })], 0, 2))
      .mockResolvedValueOnce(page([item({ id: 2, title: '둘째 장' })], 1, 2))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '더 보기' }))
    expect(get).toHaveBeenLastCalledWith(1)
    expect(await screen.findByText('둘째 장')).toBeInTheDocument()
    expect(screen.getByText('첫 장')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull()
  })

  it('shows an error with retry', async () => {
    vi.spyOn(push, 'getNotifications').mockRejectedValueOnce(new Error('down')).mockResolvedValueOnce(page([item()]))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('가을 특가')).toBeInTheDocument()
  })
})

describe('formatRelativeTime', () => {
  const now = new Date('2026-09-27T12:00:00Z')
  it('prints 방금 / N분 전 / N시간 전 then local dates', () => {
    expect(formatRelativeTime('2026-09-27T11:59:30', now)).toBe('방금')
    expect(formatRelativeTime('2026-09-27T11:15:00', now)).toBe('45분 전')
    expect(formatRelativeTime('2026-09-26T12:00:01', now)).toBe('23시간 전')
    const d = new Date('2026-09-20T12:00:00Z')
    expect(formatRelativeTime('2026-09-20T12:00:00', now)).toBe(`${d.getMonth() + 1}월 ${d.getDate()}일`)
    const old = new Date('2025-09-20T12:00:00Z')
    expect(formatRelativeTime('2025-09-20T12:00:00', now)).toBe(`${old.getFullYear()}.${old.getMonth() + 1}.${old.getDate()}`)
    expect(formatRelativeTime(null, now)).toBe('')
  })
})
