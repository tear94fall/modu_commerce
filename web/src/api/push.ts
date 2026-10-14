import type { Page } from './catalog'
import { api } from './client'
import { RecentMap } from './recent'

/** 마케팅 푸시 수신 동의. 시각은 UTC 이고 시간대 표시가 없다(다른 서버 시각과 같음). 한 번도 바꾼 적 없으면 null. */
export interface PushConsent {
  marketing: boolean
  marketingUpdatedAt: string | null
  /** 야간(21시~08시) 수신. 마케팅이 꺼져 있으면 서버가 항상 false 로 둔다. */
  night: boolean
  nightUpdatedAt: string | null
}

export const getPushConsent = () => api<PushConsent>('/api-public/v1/me/push/consent')

/** 바뀐 항목의 updatedAt 만 새로 찍힌다. */
export const updatePushConsent = (body: { marketing: boolean; night: boolean }) =>
  api<PushConsent>('/api-public/v1/me/push/consent', { method: 'PUT', body: JSON.stringify(body) })

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

// ---- 방금 읽은 알림(복제 지연 덮개, recent.ts) ----

/** 방금 읽음으로 바꾼 알림 id. 읽음은 되돌아가지 않으니 늦은 목록이 안 읽음으로 줘도 읽음으로 보인다. */
const readIds = new RecentMap<number, true>()
/** '모두 읽음' 때 보이던 가장 큰 알림 id. 이 이하는 모두 읽음. */
const readAll = new RecentMap<'all', number>()
/** 아직 끝나지 않은 읽음 요청. 뱃지·목록은 이것들이 끝난 뒤에 읽는다(안 그러면 읽기 전 수를 받는다). */
const pending = new Set<Promise<unknown>>()

function track<T>(p: Promise<T>): Promise<T> {
  pending.add(p)
  const done = () => pending.delete(p)
  p.then(done, done)
  return p
}

const settled = () => Promise.allSettled([...pending])

/** 읽음으로 바꿨다는 알림(떠 있는 뱃지가 바로 줄이고 다시 센다). detail: { delta: -1 } 또는 { all: true }. */
export const UNREAD_CHANGED = 'modu:unread-changed'
export interface UnreadChange {
  delta?: number
  all?: boolean
}
const announce = (detail: UnreadChange) => window.dispatchEvent(new CustomEvent<UnreadChange>(UNREAD_CHANGED, { detail }))

const isRecentlyRead = (n: NotificationItem) => readIds.has(n.id) || n.id <= (readAll.get('all') ?? -1)

/** 최근 30일, 최신순. 방금 읽은 것은 읽음으로. */
export const getNotifications = (page = 0, size = 20) =>
  settled()
    .then(() => api<Page<NotificationItem> & { size: number }>(`/api-public/v1/me/notifications?page=${page}&size=${size}`))
    .then((res) => ({ ...res, content: res.content.map((n) => (!n.read && isRecentlyRead(n) ? { ...n, read: true } : n)) }))

/**
 * 상단바 뱃지용. 가입 전(403)이어도 가입 화면으로 보내지 않는다.
 * 서버는 이 수를 master 에서 센다. 여기서는 보낸 읽음 요청이 끝나기를 기다린 뒤 묻는다(알림을 누르자마자 화면이 바뀌어도).
 */
export const getUnreadNotificationCount = () =>
  settled()
    .then(() => api<{ unread: number }>('/api-public/v1/me/notifications/unread-count', { quiet: true }))
    .then((r) => r.unread)

/** 안 읽은 알림만 부른다(뱃지를 하나 줄인다). */
export const markNotificationRead = (id: number) => {
  readIds.set(id, true)
  const request = track(api<void>(`/api-public/v1/me/notifications/${id}/read`, { method: 'POST' }))
  announce({ delta: -1 })
  return request.catch((e: unknown) => {
    readIds.delete(id)
    announce({})
    throw e
  })
}

/** [upToId] 는 화면에 보이던 가장 큰 알림 id(그 뒤에 온 알림은 안 읽음 그대로 둔다). */
export const markAllNotificationsRead = (upToId?: number) => {
  if (upToId !== undefined) readAll.set('all', upToId)
  const request = track(api<void>('/api-public/v1/me/notifications/read-all', { method: 'POST' }))
  announce({ all: true })
  return request.catch((e: unknown) => {
    readAll.delete('all')
    announce({})
    throw e
  })
}
