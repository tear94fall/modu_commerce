import type { Page } from './catalog'
import { api } from './client'

/** 마케팅 푸시 수신 동의. 시각은 UTC 이고 시간대 표시가 없다(다른 서버 시각과 같음). 한 번도 바꾼 적 없으면 null. */
export interface PushConsent {
  marketing: boolean
  marketingUpdatedAt: string | null
  /** 야간(21시~08시) 수신. 마케팅이 꺼져 있으면 서버가 항상 false 로 둔다. */
  night: boolean
  nightUpdatedAt: string | null
}

export const getPushConsent = () => api<PushConsent>('/api/v1/me/push/consent')

/** 바뀐 항목의 updatedAt 만 새로 찍힌다. */
export const updatePushConsent = (body: { marketing: boolean; night: boolean }) =>
  api<PushConsent>('/api/v1/me/push/consent', { method: 'PUT', body: JSON.stringify(body) })

/** 알림함 한 줄. 받은 푸시(광고) 한 건. receivedAt 은 UTC 이고 시간대 표시가 없다. */
export interface NotificationItem {
  id: number
  campaignId: number
  title: string
  body: string
  imageUrl: string | null
  /** 눌렀을 때 열 화면. ModuWeb.navigate 와 같은 허용 목록을 통과할 때만 연다. */
  path: string
  receivedAt: string
  read: boolean
}

/** 최근 30일, 최신순. */
export const getNotifications = (page = 0, size = 20) => api<Page<NotificationItem> & { size: number }>(`/api/v1/me/notifications?page=${page}&size=${size}`)

export const getUnreadNotificationCount = () => api<{ unread: number }>('/api/v1/me/notifications/unread-count').then((r) => r.unread)

export const markNotificationRead = (id: number) => api<void>(`/api/v1/me/notifications/${id}/read`, { method: 'POST' })

export const markAllNotificationsRead = () => api<void>('/api/v1/me/notifications/read-all', { method: 'POST' })
